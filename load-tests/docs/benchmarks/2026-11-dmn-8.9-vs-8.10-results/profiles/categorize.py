import sys,re,glob,collections
sys.path.insert(0,'.')
from fg_common import load_frames
CATS=[('feel-parse',r'feel/impl/parser|ExpressionValidator|FeelEngine\.parse|fastparse'),
      ('dmn-eval',r'DecisionEvaluationEvaluateProcessor|org/camunda/dmn/DmnEngine\.eval|org/camunda/feel'),
      ('dmn-parse',r'DmnParser|DmnEngine\.parse|ParsedDmn|DecisionRequirementsCache|DmnScalaDecisionEngine\.parse'),
      ('exporter',r'(?i)exporter'),
      ('jfr',r'jdk/jfr'),
      ('gc',r'G1|GCTask|ConcurrentGC|ZGC|GangWorker|WorkerThread::run'),
]
def cat(path):
    for n,rx in CATS:
        if any(re.search(rx,p) for p in path): return n
    return 'other'
def run(f):
    t,fr=load_frames(f); stack={}; ws={}; c=collections.Counter()
    items=[]
    for lvl,title,w in fr:
        stack[lvl]=title
        for k in [k for k in stack if k>lvl]: del stack[k]
        items.append((lvl,w,[stack[i] for i in range(lvl+1) if i in stack]))
    # self = width - sum of direct children
    child=collections.Counter()
    parent_idx={}
    last_at={}
    for i,(lvl,w,p) in enumerate(items):
        last_at[lvl]=i
        if lvl>0 and lvl-1 in last_at: child[last_at[lvl-1]]+=w
    for i,(lvl,w,p) in enumerate(items):
        s=w-child[i]
        if s>0: c[cat(p)]+=s
    return t,c
rows=collections.defaultdict(collections.Counter)
for f in sorted(glob.glob('/tmp/dmn-prof/p-*/*/*-cpu.html')):
    ns=re.search(r'c8-aj-(dmn\d+-[sml])',f).group(1)
    t,c=run(f); c['total']=t; rows[ns]+=c
names=['total']+[n for n,_ in CATS]+['other']
print('run'.ljust(12), *names)
for ns in sorted(rows): print(ns.ljust(12), *[f"{rows[ns][n]/30000:.3f}" for n in names])

def top_in(pattern_ns, n=12):
    c=collections.Counter(); tot=0
    for f in glob.glob(f'/tmp/dmn-prof/p-*/*{pattern_ns}*/*-cpu.html'):
        t,fr=load_frames(f); stack={}; items=[]; last_at={}; child=collections.Counter()
        for lvl,title,w in fr:
            stack[lvl]=title
            for k in [k for k in stack if k>lvl]: del stack[k]
            items.append((lvl,w,title,[stack[i] for i in range(lvl+1)]))
        for i,(lvl,w,_,_) in enumerate(items):
            last_at[lvl]=i
            if lvl>0: child[last_at[lvl-1]]+=w
        for i,(lvl,w,title,p) in enumerate(items):
            s=w-child[i]
            if s>0 and cat(p) in ('dmn-eval','feel-parse'): c[re.sub(r'\$\$Lambda.*','$$Lambda',title)]+=s; tot+=s
    print(pattern_ns, 'dmn samples', tot)
    for k,v in c.most_common(n): print(f'  {v/30000:.4f} cores {k[:110]}')
if len(sys.argv)>1:
    for a in sys.argv[1:]: top_in(a)
