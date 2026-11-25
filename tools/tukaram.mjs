// Parse + repair + validate Tukaram Gatha text from Marathi Wikisource (cached in content/raw).
import { getText } from "./wslib.mjs";

const D = "०१२३४५६७८९";
const dev = (n) => [...String(n)].map(d => D[d]).join("");
const toDevDigits = (s) => s.replace(/[0-9]/g, c => D[+c]);
const toNum = (s) => Number([...s].map(c => (D.indexOf(c) >= 0 ? D.indexOf(c) : c)).join(""));
export const RANGES = [[1,300],[301,600],[601,900],[901,1200],[1201,1500],[1501,1800],[1801,2100],[2101,2400],[2401,2700],[2701,3000],[3001,3300],[3301,3600],[3601,3900],[3901,4200],[4201,4583]];

const VIRAMA = "्";
const ZWJ = "‍";
const ZWNJ = "‌";
const C1 = "\u0086"; // control char the legacy conversion left where ऱ् was lost
const MATRAS = new Set([..."ािीुूृॄेैोौंँःॅॉॆॊ"]);
const isCons = (c) => !!c && ((c >= "क" && c <= "ह") || (c >= "क़" && c <= "य़"));
const isIndepVowel = (c) => !!c && c >= "ऄ" && c <= "औ";

/** Parse one Wikisource gatha page into raw abhangs: {n, section, lines[]} */
export function parsePage(text) {
  let t = text.slice(text.indexOf("}}") + 2);
  t = t.replace(/<\/?poem>/g, "").replace(/\[\[वर्ग:[^\]]*\]\]/g, "").replace(/\{\{[^}]*\}\}/g, "");
  const out = []; let cur = null; let section = ""; let remaining = 0;
  for (const raw of t.split(/\r?\n/)) {
    const l = raw.replace(/[‎‏]/g, "").trim();
    if (!l) continue;
    const num = l.match(/^([०-९0-9]+)\s*\.?\s*$/);
    if (num) {
      if (cur) out.push(cur);
      cur = { n: toNum(toDevDigits(num[1])), section: remaining > 0 ? section : "", lines: [] };
      if (remaining > 0) remaining--;
      continue;
    }
    const head = l.match(/^(.+?)\s*-\s*(?:अभंग|ओव्या|श्लोक|श्लोकरूपी अभंग)\s*[॥|]*\s*([०-९0-9]+)?/);
    if (head && !/[।॥]/.test(l.replace(/\s*-.*$/, "")) && l.length < 80) {
      section = head[1].trim(); remaining = head[2] ? toNum(toDevDigits(head[2])) : 1;
      if (cur) { out.push(cur); cur = null; }
      continue;
    }
    if (cur) cur.lines.push(toDevDigits(l.replace(/\|\|/g, "॥")));
  }
  if (cur) out.push(cur);
  return out;
}

export async function loadAll() {
  const res = [];
  for (const [a, b] of RANGES) {
    const items = parsePage(await getText(`तुकाराम गाथा/गाथा ${dev(a)} ते ${dev(b)}`));
    for (const it of items) res.push({ ...it, corrupted: a >= 601 });
  }
  return res;
}

/** Move a displaced reph (written as ऩ) in front of the preceding consonant cluster. */
function fixReph(s) {
  s = s.replace(/इऩ/g, "ई");
  // some pages use a stray अ (after a consonant or vowel sign) as the displaced reph glyph
  s = s.replace(/([क-हा-ौ])अ(?=[\s।॥]|$)/g, "$1ऩ");
  const chars = [...s];
  for (let i = 0; i < chars.length; i++) {
    const c = chars[i];
    if (c !== "ऩ" && c !== "ॉ") continue;
    let j = i - 1;
    while (j >= 0 && MATRAS.has(chars[j]) && chars[j] !== "ॉ") j--;
    if (!isCons(chars[j])) continue; // leave as a marker -> abhang will be rejected
    let start = j;
    while (start - 2 >= 0 && chars[start - 1] === VIRAMA && isCons(chars[start - 2])) start -= 2;
    chars.splice(i, 1, ...(c === "ॉ" ? ["ा"] : []));
    chars.splice(start, 0, "र", VIRAMA);
    i += c === "ॉ" ? 2 : 1;
  }
  return chars.join("");
}

/** A vowel sign ि that follows another matra / ि / vowel letter / word start is misplaced: move it after the next cluster. */
function fixShortI(s) {
  const chars = [...s];
  for (let i = 0; i < chars.length; i++) {
    if (chars[i] !== "ि") continue;
    const p = chars[i - 1];
    const misplaced = i === 0 || p === "ि" || MATRAS.has(p) || isIndepVowel(p) || /\s/.test(p);
    if (!misplaced) continue;
    let k = i + 1;
    if (!isCons(chars[k])) continue;
    k++;
    while (chars[k] === "़") k++;
    while (chars[k] === VIRAMA && isCons(chars[k + 1])) { k += 2; while (chars[k] === "़") k++; }
    chars.splice(i, 1); chars.splice(k - 1, 0, "ि");
  }
  return chars.join("");
}

/** Word-level fixes for ि that the legacy conversion left before a conjunct it belongs to (adjudicated from the corpus). */
function fixAmbiguousI(s) {
  return s.split(/(\s+)/).map(w => {
    if (/^(रिक्त|सिक्त)/.test(w)) return w;
    if (/भिक्षा|भिक्षु|निक्षेप|भिक्षेकरी|दीक्षा/.test(w)) return w;
    w = w.replace(/संपित्त/g, "संपत्ति").replace(/उत्पित्त/g, "उत्पत्ति").replace(/विपित्त/g, "विपत्ति");
    w = w.replace(/िक्त/g, "क्ति");
    w = w.replace(/पिक्ष/g, "पक्षि").replace(/दिक्षण/g, "दक्षिण").replace(/रिक्ष/g, "रक्षि").replace(/भिक्षलें/g, "भक्षिलें");
    w = w.replace(/िस्थ/g, "स्थि");
    return w;
  }).join("");
}

export function repairLine(line) {
  let s = line.replace(/[‎‏]/g, "");
  s = s.replace(new RegExp("र" + VIRAMA + ZWJ, "g"), "ऱ" + VIRAMA);
  s = s.replace(new RegExp(C1 + "(?=य)", "g"), "ऱ" + VIRAMA);
  s = s.replace(/Yा/g, "ज्ञ").replace(/H/g, "क्त").replace(/I/g, "क्ष्").replace(/k/g, "त्त्");
  // glyph stand-ins from the legacy font: ए after a consonant = ्य, ऑ after a sign/consonant = ळ्, ळय = ळ्य
  s = s.replace(/([क-ह])ए/g, "$1्य").replace(/([क-हा-ौ])ऑ/g, "$1ळ्");
  s = fixReph(s);
  s = fixShortI(s);
  s = fixAmbiguousI(s);
  s = s.replace(/ळय(?=[ाां])/g, "ळ्य");
  s = s.replace(/ॠ/g, "ऋ").replace(/ॡ/g, "ऌ").replace(/(\S)इप(?=[\s।॥]|$)/g, "$1ई");
  s = s.replace(/॥\s*।\s*([०-९]+)\s*॥/g, "॥$1॥").replace(/\s+([।॥])/g, " $1");
  return s;
}

/** Residual markers that mean the text is still damaged. */
export function hasDamage(s) {
  if (/[A-Za-z<>\[\]{}ऩ]/.test(s)) return true;
  if (s.includes(C1) || s.includes(ZWJ) || s.includes(ZWNJ)) return true;
  if (/ािि|िि|[ािीुूेैोौ]ि/.test(s)) return true;                 // ि after a matra / another ि
  if (/(^|[\sं])ि|[ऄ-औ]ि/.test(s)) return true;      // ि after a vowel letter, anusvara or word start
  if (/[क-ह]अ(?=[\s।॥]|$)/.test(s)) return true;                   // stray अ glyph standing in for a lost sign
  if (/ाौ|ाु|ऴ|णय|फ्(?!र)/.test(s)) return true;                    // sequences that never occur in correct text
  return false;
}
export const words = (s) => s.split(/[\s।॥,.;:?!'"‘’“”()\-–]+/).filter(Boolean).filter(w => !/^[०-९]+$/.test(w) && w !== "ध्रु");
