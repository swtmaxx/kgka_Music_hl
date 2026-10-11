"""KA Music 原生版静态检查器（不依赖编译器）

三个检查：
  A. onXxx 回调：函数体里用到的 onXxx 必须在签名/局部/lambda 参数里
  B. 具名实参：调用点传的 `x =` 必须存在于被调函数签名
  C. 必填参数：调用点必须传齐无默认值的参数
  D. core 扩展函数：用了 .long(...) 之类必须 import（Kotlin 不会自动跨包解析）
  E. R 资源类：用了 R.drawable.* 必须 import com.swtmaxx.kamusic.compose.R

设计要点（踩过的坑）：
  * `->` 里的 `>` 会污染 <> 深度计数 → 统一先 replace('->','  ')
  * 字符串字面量必须换成占位符而不是删除，否则 `F("x")` 会变成 `F()` 误判漏参
  * 尾随 lambda `F(...) { }` 要算作「最后一个参数已传」
"""
import re, pathlib, sys
from collections import defaultdict

def strip(src):
    out=[];i=0;n=len(src)
    while i<n:
        c=src[i]
        if c=='/' and i+1<n and src[i+1]=='/':
            j=src.find('\n',i); i=n if j<0 else j; continue
        if c=='/' and i+1<n and src[i+1]=='*':
            j=src.find('*/',i+2)
            if j<0: i=n
            else: out.append('\n'*src.count('\n',i,j)); i=j+2
            continue
        if src[i:i+3]=='"""':
            j=src.find('"""',i+3); out.append('S'); i=n if j<0 else j+3; continue
        if c=='"':
            i+=1
            while i<n and src[i]!='"':
                if src[i]=='\\': i+=1
                i+=1
            i+=1; out.append('S'); continue
        if c=="'":
            i+=1
            while i<n and src[i]!="'":
                if src[i]=='\\': i+=1
                i+=1
            i+=1; out.append('S'); continue
        out.append(c); i+=1
    return ''.join(out)

def match(s, start, o, c):
    d=0; i=start
    while i<len(s):
        if s[i]==o: d+=1
        elif s[i]==c:
            d-=1
            if d==0: return i
        i+=1
    return -1

def split_top(txt):
    txt = txt.replace('->','  ')          # ★ 关键：'->' 的 '>' 不是泛型闭合
    d=0; buf=''; parts=[]
    for ch in txt:
        if ch in '([<{': d+=1
        elif ch in ')]>}': d-=1
        if ch==',' and d==0: parts.append(buf); buf=''
        else: buf+=ch
    parts.append(buf); return parts

PARAM = re.compile(
    r'\s*(?:(?:private|internal|public|protected|override|'
    r'vararg|crossinline|noinline|val|var)\s+)*(\w+)\s*:')

def params_ordered(txt):
    res=[]
    for part in split_top(txt):
        m = PARAM.match(part)
        if m:
            has_default = bool(re.search(r'=(?!=)', part.split(':',1)[1]))
            res.append((m.group(1), has_default))
    return res

FUN  = re.compile(r'\bfun\s+(\w+)\s*[<(]')
DECL = re.compile(r'\b(?:fun|class)\s+(\w+)\s*(?:<[^>]*>)?\s*\(')

root = pathlib.Path(sys.argv[1] if len(sys.argv)>1 else '.')
files = {}
defs  = defaultdict(list)
for p in sorted(root.rglob('*.kt')):
    s = strip(p.read_text()); files[p]=s
    for m in DECL.finditer(s):
        k=m.end()-1; e=match(s,k,'(',')')
        if e>0: defs[m.group(1)].append(params_ordered(s[k+1:e]))

decl_spans={}
for p,s in files.items():
    sp=set()
    for m in DECL.finditer(s):
        k=m.end()-1; e=match(s,k,'(',')')
        if e>0: sp.update(range(m.start(), e+1))
    decl_spans[p]=sp

errs=[]

# ---- A. onXxx 回调 ----
for p,s in files.items():
    for m in FUN.finditer(s):
        name=m.group(1)
        k=s.find('(', m.end()-1)
        if k<0: continue
        pc=match(s,k,'(',')')
        if pc<0: continue
        params={n for n,_ in params_ordered(s[k+1:pc])}
        b=s.find('{', pc)
        if b<0 or '=' in s[pc+1:b] or ';' in s[pc+1:b]: continue
        bc=match(s,b,'{','}')
        if bc<0: continue
        body=s[b:bc]
        loc=set(re.findall(r'\b(?:val|var)\s+(\w+)', body))
        for hdr in re.findall(r'\{([^{}]*?)\s*->', body, re.S):
            for part in hdr.strip().strip('()').split(','):
                pm=re.match(r'\s*(?:vararg\s+)?(\w+)', part)
                if pm: loc.add(pm.group(1))
        for um in re.finditer(r'(?<![\w.:])(on[A-Z]\w*)(?![\w])', body):
            if re.match(r'\s*=(?!=)', body[um.end():um.end()+2]): continue
            u=um.group(1)
            if u not in params and u not in loc:
                errs.append(f"  ❌ [A] {p}:{s.count(chr(10),0,m.start())+1}  fun {name}() 用了 {u}，签名里没有")

# ---- B/C. 调用点 ----
CALL=re.compile(r'(?<![\w.])([A-Z]\w*)\s*\(')
for p,s in files.items():
    for m in CALL.finditer(s):
        name=m.group(1)
        if name not in defs: continue
        if m.start() in decl_spans[p]: continue
        k=m.end()-1; e=match(s,k,'(',')')
        if e<0: continue
        line=s.count('\n',0,m.start())+1
        args=split_top(s[k+1:e])
        if len(args)==1 and not args[0].strip(): args=[]
        # 尾随 lambda
        tail=s[e+1:e+400].lstrip()
        trailing = tail.startswith('{')
        # B. 具名实参存在性（多定义时取并集，避免误报）
        known={n for ps in defs[name] for n,_ in ps}   # ★ defs 存的是 (名字, 有无默认值) 元组
        for a in args:
            am=re.match(r'\s*(\w+)\s*=(?!=)', a)
            if am and am.group(1) not in known:
                errs.append(f"  ❌ [B] {p}:{line}  {name}(...) 传了 `{am.group(1)} =`，签名里没有"); break
        # C. 必填参数漏传（仅当定义唯一）
        if len(defs[name])==1:
            params=defs[name][0]
            supplied=set(); pos=0
            for a in args:
                am=re.match(r'\s*(\w+)\s*=(?!=)', a)
                if am: supplied.add(am.group(1))
                else:
                    if pos<len(params): supplied.add(params[pos][0])
                    pos+=1
            if trailing and params: supplied.add(params[-1][0])
            missing=[n for n,d in params if not d and n not in supplied]
            if missing:
                errs.append(f"  ❌ [C] {p}:{line}  {name}(...) 漏传必填参数 {missing}")

# ---- D. core 扩展函数的 import ----
core_dir = root.parent.parent / 'kotlin' / 'com' / 'swtmaxx' / 'kamusic' / 'compose' / 'core'
if not core_dir.exists():
    core_dir = root / 'com' / 'swtmaxx' / 'kamusic' / 'compose' / 'core'
ext = {}
if core_dir.exists():
    for f in core_dir.glob('*.kt'):
        for m in re.finditer(r'^fun\s+(?:[\w.<>?,\s]+\.)?(\w+)\s*\(', f.read_text(), re.M):
            ext.setdefault(m.group(1), 'com.swtmaxx.kamusic.compose.core')

for p,s in files.items():
    pkg = re.search(r'^package\s+(\S+)', s, re.M)
    pkg = pkg.group(1) if pkg else ''
    if pkg == 'com.swtmaxx.kamusic.compose.core': continue
    imports = set(re.findall(r'^import\s+([\w.]+)', s, re.M))
    for name in sorted(ext):
        if not re.search(r'\.' + name + r'\s*[(<]', s): continue
        if re.search(r'^\s*(?:private\s+)?fun\s+[\w.<>?,\s]*\.' + name + r'\b', s, re.M): continue
        if f'{ext[name]}.{name}' not in imports:
            errs.append(f"  ❌ [D] {p}:  用了 .{name}(...) 但没 import {ext[name]}.{name}")

# ---- E. R 类的 import ----
for p,s in files.items():
    if not re.search(r'(?<![\w.])R\.(drawable|string|mipmap|xml|style|color)\.', s): continue
    if re.search(r'^package\s+com\.swtmaxx\.kamusic\.compose\s*$', s, re.M): continue
    if 'com.swtmaxx.kamusic.compose.R' not in set(re.findall(r'^import\s+([\w.]+)', s, re.M)):
        errs.append(f"  ❌ [E] {p}:  用了 R.drawable/R.string 但没 import com.swtmaxx.kamusic.compose.R")

print('\n'.join(errs)); print()
print(f"共 {len(errs)} 处" if errs else "✅ A/B/C/D/E 五项检查全部通过")
sys.exit(1 if errs else 0)
