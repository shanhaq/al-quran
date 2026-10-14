#!/usr/bin/env python3
# Generate app/src/main/assets/quran-data.js for Al-Quran app.
# CHMETA is reused from the masjid (deensaathi) site; the rest is app-specific.
import re, os, json

SRC = os.path.expanduser("~/deensaathi/site/quran-data.js")
OUT = os.path.expanduser("~/al-quran/app/src/main/assets/quran-data.js")
URJSON = os.path.expanduser("~/al-quran/tools/ur_mp3.json")

src = open(SRC, encoding="utf-8").read()
i = src.index("var CHMETA=")
j = src.index("\n];", i) + 3
chmeta = src[i:j].strip()

juz = ("var JUZ=[[1,1,1],[2,2,142],[3,2,253],[4,3,93],[5,4,24],[6,4,148],[7,5,82],"
       "[8,6,111],[9,7,88],[10,8,41],[11,9,93],[12,11,6],[13,12,53],[14,15,1],"
       "[15,17,1],[16,18,75],[17,21,1],[18,23,1],[19,25,21],[20,27,56],[21,29,46],"
       "[22,33,31],[23,36,28],[24,39,32],[25,41,47],[26,46,1],[27,51,31],[28,58,1],"
       "[29,67,1],[30,78,1]];")

langs = (
 "var LANGS=[\n"
 " {id:85,n:'English',q:'English',b:1,t:'en'},\n"
 " {id:54,n:'Urdu',q:'اردو',rtl:1,t:'ur'},\n"
 " {id:122,n:'Hindi',q:'हिंदी',t:'hi'},\n"
 " {id:161,n:'Bengali',q:'বাংলা',t:'bn'},\n"
 " {id:133,n:'Tamil',q:'தமிழ்',t:'ta'},\n"
 " {id:227,n:'Telugu',q:'తెలుగు',t:'te'},\n"
 " {id:80,n:'Malayalam',q:'മലയാളം',t:'ml'},\n"
 " {id:771,n:'Kannada',q:'ಕನ್ನಡ',t:'kn'},\n"
 " {id:225,n:'Gujarati',q:'ગુજરાતી',t:'gu'},\n"
 " {id:226,n:'Marathi',q:'मराठी',t:'mr'},\n"
 " {id:29,n:'Persian',q:'فارسی',rtl:1,t:'fa'},\n"
 " {id:33,n:'Indonesian',q:'Indonesia',t:'id'},\n"
 " {id:77,n:'Turkish',q:'Türkçe',t:'tr'},\n"
 " {id:31,n:'French',q:'Français',t:'fr'},\n"
 " {id:83,n:'Spanish',q:'Español',t:'es'}\n"
 "];")

qaris = (
 "var QARIS=[\n"
 " {id:'alafasy',n:'Mishary Alafasy',base:'https://verses.quran.com/Alafasy/mp3/'},\n"
 " {id:'sudais',n:'Abdur-Rahman as-Sudais',base:'https://everyayah.com/data/Abdurrahmaan_As-Sudais_192kbps/'},\n"
 " {id:'shuraim',n:'Saud Ash-Shuraim',base:'https://everyayah.com/data/Saud_Al_Shuraim_128kbps/'},\n"
 " {id:'basit',n:'Abdul Basit (Murattal)',base:'https://everyayah.com/data/Abdul_Basit_Murattal_192kbps/'},\n"
 " {id:'minshawi',n:'Mohamed Minshawi',base:'https://everyayah.com/data/Minshawy_Murattal_128kbps/'},\n"
 " {id:'husary',n:'Mahmoud Al-Husary',base:'https://everyayah.com/data/Husary_128kbps/'}\n"
 "];")

# Real recorded Urdu tarjuma audio (Sudais/Shuraim Arabic + Qari Fateh Muhammad Urdu),
# one mp3 per surah from nooresunnat.com. Map {surah_number: filename}.
ur = {}
if os.path.exists(URJSON):
    m = json.load(open(URJSON))
    rows = sorted(((int(k), v) for k, v in m.items()))
    ur = ("/* Urdu tarjuma audio (Sudais & Shuraim Arabic + Qari Fateh Muhammad Urdu) — per-surah */\n"
          "var UR_AUDIO={\n" + ",\n".join(' %d:"%s"' % (n, f) for n, f in rows) + "\n};\n")

out = ("/* Al-Quran app — surah meta, juz, languages, qaris */\n" + chmeta + "\n" + juz + "\n"
       + langs + "\n" + qaris + "\n" + ur)
open(OUT, "w", encoding="utf-8").write(out)

surahs = re.findall(r"\[\d+,", chmeta)
print("written", OUT, os.path.getsize(OUT), "bytes; surah rows:", len(surahs), "ur entries:", len(ur and json.load(open(URJSON))))
