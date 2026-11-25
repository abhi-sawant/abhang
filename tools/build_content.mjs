// Builds app/src/main/assets/abhangs.json from the cached public-domain sources.
// Usage: node tools/build_content.mjs [--report]
import fs from "node:fs";
import { getText } from "./wslib.mjs";
import { loadAll, repairLine, hasDamage } from "./tukaram.mjs";
import { mahiti } from "./haripath.mjs";

const OUT = new URL("../app/src/main/assets/abhangs.json", import.meta.url);
const REPORT = process.argv.includes("--report");
const D = "०१२३४५६७८९";
const dev = (n) => [...String(n)].map(d => D[d]).join("");

const CATEGORIES = [
  { id: 1, name: "नमन / गण", description: "गणेश, सरस्वती, गुरू व विठ्ठल-रखुमाईला वंदन" },
  { id: 2, name: "रूपाचे अभंग", description: "विठ्ठलाच्या सगुण रूपाचे वर्णन" },
  { id: 3, name: "संत-स्तुती", description: "संत व वारकरी परंपरेची महती" },
  { id: 4, name: "उपदेश / प्रामाणिक अभंग", description: "भक्ती, आचरण व नामस्मरणाचा उपदेश" },
  { id: 5, name: "विरहिणी / भावगीत", description: "विठ्ठलाच्या भेटीची ओढ, माऊलीशी नाते" },
  { id: 6, name: "भारूड / गवळण / कीर्तन-शैली", description: "लोकशैलीतील, कथनात्मक व उत्साही रचना" },
  { id: 7, name: "गजर व नामस्मरण", description: "टाळ-मृदंगासह नामाचा गजर" },
  { id: 8, name: "हरिपाठ", description: "संत ज्ञानेश्वरांचा हरिपाठ" },
  { id: 9, name: "समाप्ती", description: "पसायदान, आरती व जयघोष" },
];

// ---------- helpers ----------
const clean = (s) => s.replace(/\s+/g, " ").replace(/\s+([।॥,])/g, " $1").trim();
const titleOf = (lines) => {
  let t = lines[0].split(/[।॥]/)[0];
  t = t.replace(/[०-९]+/g, "").replace(/\s+/g, " ").trim();
  if (t.length < 4 && lines[1]) t = (t + " " + lines[1].split(/[।॥]/)[0]).trim();
  return t.length > 70 ? t.slice(0, 67).trim() + "…" : t;
};
const stripTrailingVirama = (s) => s.replace(/्(?=[\s।॥,]|$)/g, "");
/** Modern Marathi spelling for the old "ह्म" forms (ह्मणे -> म्हणे, आह्मी -> आम्ही); leaves ब्रह्म etc. alone. */
const modernHma = (s) => s.replace(/(^|[\sा-ौंअ-औ])ह्म/g, "$1म्ह");
const countAny = (text, words) => words.reduce((n, w) => n + (text.split(w).length - 1), 0);

// ---------- classifier for Tukaram / Namdev abhangs ----------
const FOLK_SECTIONS = new Set(["जोहार","काला चेंडुफळी","कोडें","फुगड्या","लखोटा","हुंबरी","हमामा","गाई","कांडण","आडसण दळण","दळण","हाल","सुतुतू","टिपरी","विटूदांडू","मृदंग पाटया","ब्रम्हचारी फिर्याद गेला","डांका","वासुदेव","गांवगुंड","जोगी","सरवदा","मुंढा","डोई फोडा","मलंग","वैद्यगोळी","गोंधळ","कावडे","सौर्यान","वाघा","लळित","दसरा","रामचरित्र","श्लोकरूपी अभंग"]);
const RUP = ["सुंदर","सुन्दर","रूप","ध्यान","विटेवरी","कटावरी","कटीं","पीतांबर","कासे","तुळसी","मकर","कुंडल","कौस्तुभ","श्रीमुख","सावळ","गोजिर","साजिर","राजस","सगुण","मुगुट","मुकुट","कमळ","नयन","लोचन","वनमाळ","मुखकमळ","चरण","तेज","शोभ"];
const RUP_OPEN = /रूप|ध्यान|सुंदर|साजिर|गोजिर|राजस|सावळ|सुकुमार|मुखकमळ/;
const SANT = ["संत","साधु","साधू","वैष्णव","ज्ञानदेव","ज्ञानोबा","नामदेव","नामा ","एकनाथ","निवृत्ति","सोपान","मुक्ताबाई","जनाबाई","जनी ","चोखा","गोरा ","सावता","कबीर","भागवत","सज्जन","संतां"];
const NAMAN_STRONG = /गणपति|गणराया|गणेश|गजानन|गजवदन|शारदा|सरस्वती|गणाधीश|विघ्नहर/;
const NAMAN_ACT = /नमन|वंदन|वंदूं|वंदितों|नमो|नमू|नमस्कार|दंडवत/;
const VIRAH = ["भेटी","भेट ","वाट ","आर्त","कासावीस","काकुळती","तळमळ","दर्शन","विनवी","विनवितों","विनंति","धांव","धाव","माउली","माय ","मायबाप","लेंकरूं","लेकरूं","लेंकरें","पोर","चातक","उबगलों","उद्धरीं","तारीं","सोडवीं","पावें","पाव "];
const VIRAH_OPEN = /माउली|माय|भेटी|आर्त|काकुळती|कासावीस|वाट पाहे|वाट पाहें|दर्शन/;
const GAVLAN = ["गौळणी","गौळण","गवळण","यशोदा","राधा","गोपी","गोपिका","कान्हा","कान्हो","गोकुळ","पावा","वेणु","यमुना","दहीं","लोणी","गोरस","गोपाळ","मथुरा","काला"];

export function classify(a) {
  const text = a.lines.join(" ");
  const first2 = a.lines.slice(0, 2).join(" ");
  const sec = a.section || "";
  if (sec === "विराण्या") return 5;
  if (sec === "आशीर्वाद") return 9;
  if (sec === "हनुमंतस्तुति") return 3;
  if (FOLK_SECTIONS.has(sec)) return 6;
  const distinct = new Set(text.replace(/[।॥०-९.,]/g, " ").split(/\s+/).filter(Boolean));
  const total = text.replace(/[।॥०-९.,]/g, " ").split(/\s+/).filter(Boolean).length;
  if (a.lines.length <= 4 && total >= 8 && distinct.size <= 8) return 7;
  if (/आरती|ओवाळ/.test(first2)) return 9;
  if (countAny(text, RUP) >= 3 && RUP_OPEN.test(first2)) return 2;
  if (sec === "मंगलाचरण") return 1;
  if (NAMAN_STRONG.test(first2) && NAMAN_ACT.test(text)) return 1;
  if (countAny(text, GAVLAN) >= 3) return 6;
  if (countAny(first2, SANT) >= 1 && countAny(text, SANT) >= 2) return 3;
  if (countAny(text, VIRAH) >= 3 && VIRAH_OPEN.test(text)) return 5;
  return 4;
}

// ---------- sources ----------
const records = [];
const add = (r) => records.push(r);
const notes = { dup: [], dropped: 0 };

// 1. Tukaram Gatha (Marathi Wikisource) --------------------------------------
{
  const all = await loadAll();
  const seen = new Set();
  for (const a of all) {
    a.fixed = a.corrupted ? a.lines.map(repairLine) : a.lines.slice();
    if (a.fixed.length < 2) { notes.dropped++; continue; }
    if (a.fixed.some(hasDamage)) { notes.dropped++; continue; }
    if (seen.has(a.n)) { notes.dup.push(a.n); continue; }
    seen.add(a.n);
    const lines = a.fixed.map(l => modernHma(clean(stripTrailingVirama(l))));
    const base = { lines, section: a.section };
    add({
      id: 10000 + a.n, categoryId: classify(base), title: titleOf(lines), author: "संत तुकाराम",
      source: `तुकाराम गाथा, अभंग क्र. ${dev(a.n)}` + (a.corrupted ? " (स्वयंचलित दुरुस्ती; तपासणी आवश्यक)" : ""),
      body: lines.join("\n"), num: a.n, order: a.n,
    });
  }
}

// 2. Namdev Abhangagatha (Marathi Wikisource) --------------------------------
{
  const x = await getText("नामदेव अभंगगाथा");
  const b = x.slice(x.indexOf("<poem>") + 6, x.lastIndexOf("</poem>"));
  let n = 0;
  for (const part of b.split(/\n\s*\n/).map(s => s.trim()).filter(Boolean)) {
    const m = part.match(/^([०-९]+)\s*\.?\s*([\s\S]*)$/); if (!m) continue;
    n = Number([...m[1]].map(c => D.indexOf(c)).join(""));
    const verses = m[2].replace(/\s+/g, " ").split(/(?<=॥[०-९]+॥)\s*/).map(s => s.trim()).filter(Boolean);
    const lines = verses.map(v => modernHma(clean(v.replace(/\(\s*[^)]*\)/g, ""))));
    if (lines.length < 1) continue;
    const base = { lines, section: "" };
    add({ id: 20000 + n, categoryId: n <= 6 ? 1 : classify(base), title: titleOf(lines), author: "संत नामदेव", source: `नामदेव अभंगगाथा, अभंग क्र. ${dev(n)}`, body: lines.join("\n"), num: n, order: 5000 + n });
  }
}

// 3. Haripath of Sant Dnyaneshwar (mahitidarshak.com transcription) -------------
{
  for (const h of mahiti().filter(x => x.n <= 28)) {
    const ls = h.lines.map(l => clean(stripTrailingVirama(l)).replace(/पुंण्याची/g, "पुण्याची"));
    // join half-verses: "पद १ ।" + "पद २ ॥१॥" -> one line
    const lines = [];
    for (let i = 0; i < ls.length; i++) {
      if (/॥[०-९]+॥\s*$/.test(ls[i]) || /॥\s*$/.test(ls[i])) { lines.push(ls[i]); continue; }
      if (i + 1 < ls.length && /॥/.test(ls[i + 1])) { lines.push(ls[i] + " " + ls[i + 1]); i++; } else lines.push(ls[i]);
    }
    add({ id: 30000 + h.n, categoryId: 8, title: titleOf(lines), author: "संत ज्ञानेश्वर", source: `हरिपाठ, अभंग क्र. ${dev(h.n)} (पाठ तपासणे आवश्यक)`, body: lines.join("\n"), num: h.n, order: h.n });
  }
}

// 4. Pasaydan (Marathi Wikisource) ---------------------------------------------
{
  const x = await getText("पसायदान");
  const b = x.slice(x.indexOf("<poem>") + 6, x.indexOf("</poem>"));
  const lines = b.split(/\n/).map(l => l.trim()).filter(Boolean).map(l => clean(l.replace(/\|\|/g, "॥").replace(/,/g, " ।").replace(/॥([०-९]+)\s*॥?/g, "॥$1॥")));
  const grouped = []; for (let i = 0; i < lines.length; i += 2) grouped.push(lines.slice(i, i + 2).join(" "));
  add({ id: 40001, categoryId: 9, title: "पसायदान", author: "संत ज्ञानेश्वर", source: "ज्ञानेश्वरी, अध्याय १८ (पाठ तपासणे आवश्यक)", body: lines.join("\n"), order: 1 });
}

// 5. Aartis and chants ---------------------------------------------------------
add({
  id: 40010, categoryId: 9, title: "युगे अठ्ठावीस विटेवरी उभा (विठ्ठलाची आरती)", author: "संत नामदेव", source: "पारंपरिक आरती (पाठ तपासणे आवश्यक)", order: 10,
  body: [
    "युगे अठ्ठावीस विटेवरी उभा ।", "वामांगी रखुमाई दिसे दिव्य शोभा ॥", "पुंडलिकाचे भेटी परब्रह्म आले गा ।", "चरणी वाहे भीमा उद्धरी जगा ॥१॥",
    "जय देव जय देव जय पांडुरंगा ।", "रखुमाईवल्लभा राईच्या वल्लभा पावें जिवलगा ॥", "॥ जय देव जय देव ॥",
    "तुळसीमाळा गळा कर ठेवुनी कटी ।", "कासे पीतांबर कस्तुरी लल्लाटी ॥", "देव सुरवर नित्य येती भेटी ।", "गरुड हनुमंत पुढे उभे राहती ॥२॥", "॥ जय देव जय देव ॥",
    "धन्य वेणुनाद अणुक्षेत्रपाळा ।", "सुवर्णाची कमळे वनमाळा गळा ॥", "राही रखुमाबाई राणीया सकळा ।", "ओवाळिती राजा विठोबा सांवळा ॥३॥", "॥ जय देव जय देव ॥",
    "ओवाळू आरत्या कुरवंड्या येती ।", "चंद्रभागेमाजी सोडुनिया देती ॥", "दिंड्या पताका वैष्णव नाचती ।", "पंढरीचा महिमा वर्णावा किती ॥४॥", "॥ जय देव जय देव ॥",
    "आषाढी कार्तिकी भक्तजन येती ।", "चंद्रभागेमध्ये स्नाने जे करिती ॥", "दर्शनहेळामात्रे तया होय मुक्ती ।", "केशवासी नामदेव भावे ओवाळिती ॥५॥", "॥ जय देव जय देव ॥",
  ].join("\n"),
});
add({
  id: 40011, categoryId: 1, title: "सुखकर्ता दुखहर्ता (श्री गणपतीची आरती)", author: "समर्थ रामदास", source: "पारंपरिक आरती (पाठ तपासणे आवश्यक)", order: 11,
  body: [
    "सुखकर्ता दुखहर्ता वार्ता विघ्नांची ।", "नुरवी पुरवी प्रेम कृपा जयाची ।", "सर्वांगी सुंदर उटी शेंदुराची ।", "कंठी झळके माळ मुक्ताफळांची ॥१॥",
    "जय देव जय देव जय मंगलमूर्ती ।", "दर्शनमात्रे मनकामना पुरती ॥धृ॥",
    "रत्नखचित फरा तुज गौरीकुमरा ।", "चंदनाची उटी कुमकुम केशरा ।", "हिरेजडित मुकुट शोभतो बरा ।", "रुणझुणती नूपुरे चरणी घागरिया ॥२॥", "जय देव जय देव जय मंगलमूर्ती ।",
    "लंबोदर पीतांबर फणिवरबंधना ।", "सरळ सोंड वक्रतुंड त्रिनयना ।", "दास रामाचा वाट पाहे सदना ।", "संकटी पावावे निर्वाणी रक्षावे सुरवरवंदना ॥३॥", "जय देव जय देव जय मंगलमूर्ती ।", "दर्शनमात्रे मनकामना पुरती ॥",
  ].join("\n"),
});
const chants = [
  ["जय जय रामकृष्ण हरी", "पारंपरिक गजर", ["जय जय रामकृष्ण हरी ।", "जय जय रामकृष्ण हरी ॥", "जय जय रामकृष्ण हरी ।", "जय जय रामकृष्ण हरी ॥"], 1],
  ["ज्ञानोबा माऊली तुकाराम", "पारंपरिक गजर", ["ज्ञानोबा माऊली तुकाराम ।", "ज्ञानोबा माऊली तुकाराम ॥", "ज्ञानोबा माऊली तुकाराम ।", "ज्ञानोबा माऊली तुकाराम ॥"], 7],
  ["पुंडलिका वरदा हरि विठ्ठल", "पारंपरिक गजर", ["पुंडलिका वरदा हरि विठ्ठल ।", "श्री ज्ञानदेव तुकाराम ॥", "पंढरीनाथ महाराज की जय ॥"], 7],
  ["विठ्ठल विठ्ठल जय हरी विठ्ठल", "पारंपरिक गजर", ["विठ्ठल विठ्ठल जय हरी विठ्ठल ।", "विठ्ठल विठ्ठल जय हरी विठ्ठल ॥", "विठ्ठल विठ्ठल जय हरी विठ्ठल ।", "विठ्ठल विठ्ठल जय हरी विठ्ठल ॥"], 7],
  ["राम कृष्ण हरी", "पारंपरिक गजर", ["राम कृष्ण हरी ।", "राम कृष्ण हरी ॥", "राम कृष्ण हरी ।", "राम कृष्ण हरी ॥"], 7],
  ["पुंडलिक वरदा हरि विठ्ठल (समाप्ती जयघोष)", "पारंपरिक जयघोष", ["पुंडलिक वरदा हरि विठ्ठल ।", "श्री ज्ञानदेव तुकाराम ।", "श्री ज्ञानेश्वर महाराज की जय ॥"], 9],
];
chants.forEach(([title, author, lines, cat], i) => add({ id: 40100 + i, categoryId: cat, title, author, source: "पारंपरिक", body: lines.join("\n"), order: 100 + i }));

// ---------- human corrections (content/corrections.json: { "<id>": "corrected body" }) ----------
{
  const f = new URL("../content/corrections.json", import.meta.url);
  if (fs.existsSync(f)) {
    const fixes = JSON.parse(fs.readFileSync(f, "utf8"));
    let applied = 0;
    for (const r of records) {
      const c = fixes[r.id]; if (typeof c !== "string" || !c.trim()) continue;
      const lines = c.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
      r.body = lines.join("\n"); r.title = titleOf(lines);
      r.source = r.source.replace(/\s*\(स्वयंचलित दुरुस्ती; तपासणी आवश्यक\)/, " (तपासले)");
      applied++;
    }
    console.log("human corrections applied:", applied);
  }
  const rf = new URL("../content/reviewed_ok.json", import.meta.url); // ids confirmed correct as-is
  if (fs.existsSync(rf)) {
    const ok = new Set(JSON.parse(fs.readFileSync(rf, "utf8")));
    for (const r of records) if (ok.has(r.id)) r.source = r.source.replace(/\s*\(स्वयंचलित दुरुस्ती; तपासणी आवश्यक\)/, " (तपासले)");
  }
}

// ---------- output ----------
records.sort((a, b) => a.categoryId - b.categoryId || a.order - b.order);
const abhangs = records.map(({ order, ...r }) => r);
fs.writeFileSync(OUT, JSON.stringify({ categories: CATEGORIES, abhangs }));
const per = {}; for (const r of records) per[r.categoryId] = (per[r.categoryId] || 0) + 1;
console.log("written", abhangs.length, "abhangs;", (fs.statSync(OUT).size / 1024).toFixed(0), "KB; per category", JSON.stringify(per));
console.log("dropped (damaged/too short):", notes.dropped, " duplicate numbers skipped:", notes.dup.join(","));
if (REPORT) {
  for (const c of CATEGORIES) {
    const rs = records.filter(r => r.categoryId === c.id);
    console.log(`\n### ${c.id} ${c.name} (${rs.length})`);
    const step = Math.max(1, Math.floor(rs.length / 12));
    for (let i = 0; i < rs.length; i += step) console.log("  ", rs[i].source.slice(0, 24).padEnd(24), rs[i].title);
  }
}
