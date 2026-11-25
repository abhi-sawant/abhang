import { getText } from "./wslib.mjs";
const D = "०१२३४५६७८९";
const dev = (n) => [...String(n)].map(d => D[d]).join("");
const ranges = [[601,900],[901,1200],[1201,1500],[1501,1800],[1801,2100],[2101,2400],[2401,2700],[2701,3000],[3001,3300],[3301,3600],[3601,3900],[3901,4200],[4201,4583]];
let all = "";
for (const [a,b] of ranges) { const t = await getText(`तुकाराम गाथा/गाथा ${dev(a)} ते ${dev(b)}`); all += t.slice(t.indexOf("}}")+2) + "\n"; }
const freq = {};
for (const ch of all) { if (/[ऀ-ॿ\s\d.,;:'"()\-–?!‘’“”]/.test(ch)) continue; freq[ch] = (freq[ch]||0)+1; }
console.log("non-devanagari chars:", Object.entries(freq).sort((a,b)=>b[1]-a[1]).map(([c,n])=>`${c}(U+${c.codePointAt(0).toString(16)}):${n}`).join("  "));
const words = all.split(/[\s।॥]+/);
const bad = {};
for (const w of words) if (/[A-Za-z]|ऩ|ऱ|ॉ/.test(w)) bad[w] = (bad[w]||0)+1;
console.log(Object.keys(bad).length, "distinct bad words");
console.log(Object.entries(bad).sort((a,b)=>b[1]-a[1]).slice(0,80).map(([w,n])=>w+":"+n).join("  "));
