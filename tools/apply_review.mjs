// Merge review_results.json (exported from content/review/review.html) into the build inputs.
// Usage: node tools/apply_review.mjs path/to/review_results.json
import fs from "node:fs";

const file = process.argv[2];
if (!file) { console.error("usage: node tools/apply_review.mjs review_results.json"); process.exit(1); }
const results = JSON.parse(fs.readFileSync(file, "utf8"));
const cDir = new URL("../content/", import.meta.url);
const read = (n, d) => { const f = new URL(n, cDir); return fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, "utf8")) : d; };
const corrections = read("corrections.json", {});
const okSet = new Set(read("reviewed_ok.json", []));
let ok = 0, bad = 0;
for (const r of results) {
  if (r.status === "ok") { okSet.add(r.id); ok++; }
  else if (r.status === "bad" && r.corrected?.trim()) { corrections[r.id] = r.corrected; bad++; }
}
fs.writeFileSync(new URL("corrections.json", cDir), JSON.stringify(corrections, null, 1));
fs.writeFileSync(new URL("reviewed_ok.json", cDir), JSON.stringify([...okSet].sort((a, b) => a - b)));
const n = ok + bad;
console.log(`merged ${ok} confirmed-correct and ${bad} corrected abhangs.`);
if (n) console.log(`sample error rate: ${(bad * 100 / n).toFixed(0)}% (${bad}/${n}) -> expect roughly that share of the other repaired abhangs to still contain small slips.`);
console.log("now run: node tools/build_content.mjs");
