// Builds content/review/review.html: a self-contained page for reviewing a stratified random sample
// of the auto-repaired Tukaram abhangs. Usage: node tools/make_review.mjs [sampleSize=150]
import fs from "node:fs";
import { loadAll } from "./tukaram.mjs";

const N = Number(process.argv[2] || 150);
const data = JSON.parse(fs.readFileSync(new URL("../app/src/main/assets/abhangs.json", import.meta.url), "utf8"));
const repaired = data.abhangs.filter(a => a.source.includes("स्वयंचलित"));
const raw = new Map((await loadAll()).map(a => [a.n, a.lines]));

// stratified sample: split by abhang number into N equal buckets, pick one per bucket (seeded, reproducible)
let seed = 20261125; const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 2 ** 32;
const sorted = repaired.slice().sort((a, b) => a.num - b.num);
const sample = [];
for (let i = 0; i < N; i++) {
  const lo = Math.floor(i * sorted.length / N), hi = Math.floor((i + 1) * sorted.length / N);
  sample.push(sorted[lo + Math.floor(rnd() * Math.max(1, hi - lo))]);
}
const items = sample.map(a => ({ id: a.id, num: a.num, title: a.title, body: a.body, raw: (raw.get(a.num) || []).join("\n") }));

const html = `<!doctype html>
<html lang="mr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>अभंग तपासणी</title>
<style>
:root{--bg:#fff8f0;--card:#fff;--ink:#222;--mute:#666;--pri:#e65100;--ok:#2e7d32;--bad:#c62828;--line:#e6d5c3}
@media (prefers-color-scheme:dark){:root{--bg:#1b1210;--card:#2a1d19;--ink:#f1e6df;--mute:#b9a89e;--line:#4a3830}}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.6 system-ui,"Noto Sans Devanagari","Mukta",sans-serif}
header{position:sticky;top:0;background:var(--pri);color:#fff;padding:10px 16px;z-index:5;display:flex;gap:12px;align-items:center;flex-wrap:wrap}
header h1{font-size:18px;margin:0;flex:1}
header button{background:#fff;color:var(--pri);border:0;border-radius:6px;padding:6px 12px;font-weight:600;cursor:pointer}
main{max-width:760px;margin:0 auto;padding:12px 16px 80px}
.intro{color:var(--mute);font-size:14px}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px 16px;margin:14px 0}
.card.ok{border-color:var(--ok)}.card.bad{border-color:var(--bad)}
.meta{color:var(--mute);font-size:13px}
.body{white-space:pre-wrap;font-size:21px;line-height:1.8;margin:8px 0}
.row{display:flex;gap:8px;margin-top:8px}
.row button{flex:1;padding:8px;border-radius:8px;border:1px solid var(--line);background:transparent;color:var(--ink);font-size:15px;cursor:pointer}
.row button.ok.on{background:var(--ok);color:#fff;border-color:var(--ok)}
.row button.bad.on{background:var(--bad);color:#fff;border-color:var(--bad)}
textarea{width:100%;box-sizing:border-box;min-height:140px;margin-top:8px;font:inherit;font-size:19px;line-height:1.7;background:transparent;color:inherit;border:1px solid var(--line);border-radius:8px;padding:8px}
details{margin-top:8px;color:var(--mute);font-size:13px}details pre{white-space:pre-wrap;font:inherit}
#sum{font-size:14px}
</style></head><body>
<header><h1>अभंग तपासणी</h1><span id="sum"></span><button id="export">निकाल सेव्ह करा (JSON)</button></header>
<main>
<p class="intro">येथे स्वयंचलित दुरुस्ती केलेल्या अभंगांचा यादृच्छिक नमुना आहे (${items.length} / ${repaired.length}). प्रत्येक अभंग वाचा: शुद्धलेखन बरोबर असेल तर <b>✔ बरोबर</b>, चूक असेल तर <b>✘ चूक</b> दाबून खालच्या पेटीत दुरुस्त मजकूर लिहा. तुमचे उत्तर या ब्राउझरमध्ये आपोआप जतन होते. शेवटी वरील बटणाने निकाल फाइल सेव्ह करा.</p>
<div id="list"></div></main>
<script>
const ITEMS=${JSON.stringify(items)};
const KEY="abhang-review-v1";
let st={};try{st=JSON.parse(localStorage.getItem(KEY)||"{}")}catch(e){}
const save=()=>{try{localStorage.setItem(KEY,JSON.stringify(st))}catch(e){}};
const $=(s,r=document)=>r.querySelector(s);
function summary(){
  const ok=ITEMS.filter(i=>st[i.id]?.s==="ok").length,bad=ITEMS.filter(i=>st[i.id]?.s==="bad").length,done=ok+bad;
  const rate=done?Math.round(bad*100/done):0;
  $("#sum").textContent="तपासले "+done+"/"+ITEMS.length+" · चुकीचे "+bad+(done?" ("+rate+"%)":"");
}
function render(){
  const list=$("#list");list.innerHTML="";
  ITEMS.forEach((it,idx)=>{
    const s=st[it.id]||{};const c=document.createElement("div");c.className="card"+(s.s?" "+s.s:"");
    c.innerHTML='<div class="meta">'+(idx+1)+'. तुकाराम गाथा क्र. '+it.num+'</div><div class="body"></div>'+
      '<div class="row"><button class="ok">✔ बरोबर</button><button class="bad">✘ चूक</button></div>'+
      '<div class="fix" hidden><textarea></textarea></div><details><summary>स्रोतातील मूळ (बिघडलेला) मजकूर</summary><pre></pre></details>';
    $(".body",c).textContent=it.body;$("pre",c).textContent=it.raw;
    const ta=$("textarea",c);ta.value=s.t??it.body;
    const apply=()=>{c.className="card"+(st[it.id]?.s?" "+st[it.id].s:"");$(".ok",c).classList.toggle("on",st[it.id]?.s==="ok");$(".bad",c).classList.toggle("on",st[it.id]?.s==="bad");$(".fix",c).hidden=st[it.id]?.s!=="bad";summary();};
    $(".ok",c).onclick=()=>{st[it.id]={s:"ok"};save();apply()};
    $(".bad",c).onclick=()=>{st[it.id]={s:"bad",t:ta.value};save();apply()};
    ta.oninput=()=>{st[it.id]={s:"bad",t:ta.value};save();};
    list.appendChild(c);apply();
  });
}
$("#export").onclick=()=>{
  const out=ITEMS.filter(i=>st[i.id]?.s).map(i=>({id:i.id,num:i.num,status:st[i.id].s,corrected:st[i.id].s==="bad"?st[i.id].t:undefined}));
  const a=document.createElement("a");a.href=URL.createObjectURL(new Blob([JSON.stringify(out,null,1)],{type:"application/json"}));a.download="review_results.json";a.click();
};
render();summary();
</script></body></html>`;

fs.mkdirSync(new URL("../content/review/", import.meta.url), { recursive: true });
fs.writeFileSync(new URL("../content/review/review.html", import.meta.url), html);
console.log(`review page written: ${items.length} of ${repaired.length} repaired abhangs sampled`);
