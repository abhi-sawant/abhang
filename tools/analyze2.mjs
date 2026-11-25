import { loadAll, repairLine, hasDamage, words } from "./tukaram.mjs";
const all = await loadAll();
for (const a of all) a.fixed = a.corrupted ? a.lines.map(repairLine) : a.lines;
const V = new Set(); for (const a of all.filter(a => !a.corrupted)) for (const w of words(a.fixed.join(" "))) V.add(w);
// leftover damaged words
const dw = new Map();
for (const a of all) for (const w of words(a.fixed.join(" "))) if (hasDamage(w)) dw.set(w, (dw.get(w) || 0) + 1);
console.log("damaged distinct words:", dw.size); console.log([...dw].sort((x,y)=>y[1]-x[1]).slice(0,70).map(([w,n])=>w+":"+n).join("  "));
// ambiguous i+conjunct
const amb = new Map();
for (const a of all.filter(a => a.corrupted)) for (const w of words(a.fixed.join(" "))) {
  if (hasDamage(w)) continue;
  const m = w.match(/ि([क-ह](?:्[क-ह])+)/); if (!m) continue;
  amb.set(w, (amb.get(w) || 0) + 1);
}
const moveI = (w) => w.replace(/ि([क-ह](?:्[क-ह])+)/, "$1ि");
const keep = [], fix = [], unk = [];
for (const [w, n] of amb) { if (V.has(w)) keep.push([w,n]); else if (V.has(moveI(w))) fix.push([w, moveI(w), n]); else unk.push([w, moveI(w), n]); }
console.log("ambiguous distinct:", amb.size, "keep(in clean vocab):", keep.length, "fix(B in vocab):", fix.length, "unknown:", unk.length);
console.log("FIX:", fix.map(f=>f[0]+"→"+f[1]+":"+f[2]).join("  "));
console.log("UNKNOWN:", unk.map(f=>f[0]+"|"+f[1]+":"+f[2]).join("  "));
