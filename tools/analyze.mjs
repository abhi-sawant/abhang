import { loadAll, repairLine, hasDamage, words } from "./tukaram.mjs";
const all = await loadAll();
console.log("parsed", all.length, "unique nums", new Set(all.map(a => a.n)).size);
const nums = new Set(all.map(a => a.n)); const missing = []; for (let i = 1; i <= 4583; i++) if (!nums.has(i)) missing.push(i);
console.log("missing", missing.length, missing.slice(0, 30).join(","));
const dup = all.length - nums.size; console.log("duplicate numbers", dup);
// repair
for (const a of all) a.fixed = a.corrupted ? a.lines.map(repairLine) : a.lines;
const clean = all.filter(a => !a.corrupted);
const V = new Map(); for (const a of clean) for (const w of words(a.fixed.join(" "))) V.set(w, (V.get(w) || 0) + 1);
console.log("clean abhangs", clean.length, "clean vocab", V.size);
const dmg = all.filter(a => a.fixed.some(hasDamage));
console.log("damaged after repair:", dmg.length, "of", all.length, "(clean pages damaged:", dmg.filter(a=>!a.corrupted).length + ")");
// corpus-wide word frequency among undamaged text
const F = new Map(); for (const a of all) if (!a.fixed.some(hasDamage)) for (const w of words(a.fixed.join(" "))) F.set(w, (F.get(w) || 0) + 1);
const trusted = (w) => V.has(w) || (F.get(w) || 0) >= 3;
let pass = 0, passCorr = 0; const unk = new Map();
for (const a of all) {
  if (a.fixed.some(hasDamage)) continue;
  const bad = words(a.fixed.join(" ")).filter(w => !trusted(w));
  if (bad.length === 0) { pass++; if (a.corrupted) passCorr++; } else for (const w of bad) unk.set(w, (unk.get(w) || 0) + 1);
}
console.log("fully trusted abhangs:", pass, "(of which from corrupted pages:", passCorr + ")");
console.log("sample unknown words:", [...unk.keys()].slice(0, 60).join(" "));
console.log("sample repaired:\n", all.find(a => a.n === 601).fixed.join("\n"), "\n", all.find(a => a.n === 602).fixed.join("\n"));
