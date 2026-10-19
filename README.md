---
title: INFINITE-TUNE Minecraft Server
emoji: 🎮
colorFrom: green
colorTo: indigo
sdk: docker
app_port: 7860
pinned: false
---

# ⛏️ INFINITE-TUNE — Minecraft Server (Purpur + playit.gg on Hugging Face Spaces)

৪–৫ জন বন্ধু নিয়ে খেলার জন্য একটি হালকা, পারফরম্যান্স-অপ্টিমাইজড **Minecraft: Purpur (latest)**
সার্ভার, যা সম্পূর্ণভাবে **Hugging Face Spaces (Docker SDK)**-এ ফ্রি/হোস্টেড হিসেবে চলে এবং
**[playit.gg](https://playit.gg)** টানেলের মাধ্যমে ইন্টারনেটে এক্সটার্নাল আইপি/অ্যাড্রেস পায়,
যাতে বাইরে থেকে কেউ সংযুক্ত হতে পারে (Hugging Face সরাসরি Minecraft-এর TCP পোর্ট এক্সপোজ করতে দেয় না)।

## ✨ ফিচার

- **Base image:** `itzg/minecraft-server:latest` (সবচেয়ে ব্যবহৃত ও সুরক্ষিত Minecraft docker image)
- **Server type:** `PURPUR` (PaperMC/Spigot compatible, বেশি TPS ও কনফিগারযোগ্য)
- **Version:** `LATEST` (প্রতিবার বিল্ডে সর্বশেষ স্টেবল Minecraft ভার্সন)
- **RAM:** `14G` heap — Hugging Face-এর ১৬ জিবি হার্ডওয়্যারের প্রায় সর্বোচ্চ ব্যবহার, বাকি ~2G OS/অন্যান্য প্রসেসের জন্য রাখা
- **Max players:** `5`, `online-mode=false` (cracked/offline accounts দিয়েও জয়েন করা যাবে)
- **view-distance=8**, **simulation-distance=6** — ছোট গ্রুপের জন্য পারফরম্যান্স বনাম রেঞ্জের ভালো ব্যালেন্স
- Aikar-স্টাইলের **G1GC JVM ফ্ল্যাগ** দিয়ে অপ্টিমাইজড গার্বেজ কালেকশন (`-XX:+UseG1GC`, `-XX:MaxGCPauseMillis=100` ইত্যাদি), যা GC ল্যাগ/স্টাটার কমায়
- **playit.gg agent** বিল্ট-ইন — কন্টেইনারের ভিতর থেকেই আউটবাউন্ড টানেল খুলে পাবলিক অ্যাড্রেস দেয়
- পোর্ট **7860** এক্সপোজ করা আছে শুধু Hugging Face-এর হেলথ-চেক/স্ট্যাটাস পেজের জন্য (আসল গেম ট্র্যাফিক যায় playit.gg দিয়ে)

## 📁 প্রজেক্ট ফাইলসমূহ

| ফাইল | কাজ |
|---|---|
| `Dockerfile` | সার্ভার ইমেজ বিল্ড করে: env vars, JVM/G1GC ফ্ল্যাগ, playit.gg agent ইনস্টল |
| `server.properties` | ডিফল্ট Minecraft সার্ভার কনফিগ (view/simulation distance, max-players, flight, command block ইত্যাদি) |
| `start.sh` | কন্টেইনার এন্ট্রিপয়েন্ট — status page + playit.gg চালু করে, তারপর আসল Minecraft সার্ভার বুট করে |
| `.gitignore` | world/log/cache ফাইল রিপোতে কমিট হওয়া থেকে আটকায় |

---

## 🚀 ধাপ ১: Hugging Face Space-এ ডিপ্লয় করা

1. [huggingface.co/new-space](https://huggingface.co/new-space) এ গিয়ে একটি নতুন Space তৈরি করুন।
2. **SDK** হিসেবে অবশ্যই **`Docker`** বেছে নিন (অন্য কিছু নয়)।
3. **Hardware:** যতটা সম্ভব বেশি RAM/CPU আছে এমন টায়ার বেছে নিন (অন্তত ১৬ জিবি RAM; ফ্রি CPU বেসিক টায়ারে পারফরম্যান্স সীমিত থাকতে পারে, প্রয়োজনে পেইড "CPU upgrade" টায়ার ব্যবহার করুন)।
4. এই রিপোজিটরির সবগুলো ফাইল আপনার Space-এর রিপোতে পুশ করুন (অথবা Space সেটিংস থেকে এই GitHub রিপো সরাসরি **"Sync with a Github repository"** দিয়ে লিঙ্ক করুন)।
5. Space নিজে থেকেই `Dockerfile` ডিটেক্ট করে বিল্ড শুরু করবে। প্রথম বিল্ডে Purpur ডাউনলোড হতে কিছুটা সময় নেবে — **Logs** ট্যাবে প্রগ্রেস দেখা যাবে।
6. বিল্ড শেষে Space এর স্ট্যাটাস **Running** দেখালে বুঝবেন Minecraft সার্ভার চালু হয়ে গেছে (Logs-এ `Done!` মেসেজ দেখা যাবে)।

> 🔒 **Persistent storage:** ফ্রি Space রিস্টার্ট হলে world ডেটা হারিয়ে যেতে পারে। স্থায়ীভাবে world সংরক্ষণ করতে চাইলে Space Settings থেকে **Persistent Storage** চালু করুন (`/data` পাথে মাউন্ট হয়, যা এই ইমেজের ডিফল্ট world লোকেশনের সাথেই মিলে যায়)।

---

## 🌐 ধাপ ২: playit.gg দিয়ে এক্সটার্নাল IP/অ্যাড্রেস নেওয়া

Hugging Face Space শুধু একটা HTTP পোর্ট (7860) বাইরে এক্সপোজ করে, Minecraft-এর raw TCP পোর্ট (25565) নয়। তাই বন্ধুদের সাথে খেলতে **playit.gg** ব্যবহার করে একটা আউটবাউন্ড টানেল খুলতে হবে।

1. [playit.gg](https://playit.gg) এ গিয়ে ফ্রি অ্যাকাউন্ট দিয়ে সাইন ইন করুন।
2. ড্যাশবোর্ড থেকে **Agents → Add Agent** এ ক্লিক করে একটি নতুন এজেন্ট তৈরি করুন এবং সেটির **Secret Key** কপি করুন।
3. আপনার Hugging Face Space-এর **Settings → Variables and secrets** এ গিয়ে একটি নতুন **Secret** যোগ করুন:
   - Name: `PLAYIT_SECRET_KEY`
   - Value: (playit.gg থেকে কপি করা secret key)
4. Secret সেভ করার পর Space-টি রিস্টার্ট করুন (Settings → Factory reboot/Restart)।
5. Space-এর **Logs** ট্যাবে `[playit]` লাইনগুলো দেখুন — এজেন্ট কানেক্ট হয়ে গেলে playit.gg ড্যাশবোর্ডে এজেন্টটি **Online** দেখাবে।
6. এখন playit.gg ড্যাশবোর্ডে ওই এজেন্টের নিচে **Add Tunnel** চাপুন:
   - Tunnel Type: **Minecraft Java**
   - Local Address: `localhost:25565`
7. playit.gg আপনাকে একটি পাবলিক অ্যাড্রেস দেবে, যেমন: `your-name.gl.joinmc.link` (পোর্ট ছাড়াই, Minecraft Java এর জন্য পোর্ট দরকার হয় না)।
8. এই অ্যাড্রেসটিই আপনার বন্ধুদের দিন — তারা Minecraft Java Edition-এ **Multiplayer → Add Server** এ গিয়ে এই অ্যাড্রেস বসিয়ে সরাসরি জয়েন করতে পারবে (সর্বোচ্চ ৫ জন, `online-mode=false` থাকায় cracked/non-premium অ্যাকাউন্টও কাজ করবে)।

---

## ⚙️ কাস্টমাইজেশন

- **OP/Admin** বানাতে Space Secret হিসেবে `OPS=YourUsername` যোগ করুন।
- **Whitelist** চালু করতে `ENABLE_WHITELIST=true` এবং `WHITELIST=user1,user2` যোগ করুন।
- `Dockerfile`-এর `ENV` সেকশনে `MEMORY`, `VIEW_DISTANCE`, `SIMULATION_DISTANCE`, `MAX_PLAYERS` ইত্যাদি পরিবর্তন করে সহজেই রি-টিউন করা যায়।
- সম্পূর্ণ env var তালিকার জন্য দেখুন: [itzg/docker-minecraft-server ডকুমেন্টেশন](https://docker-minecraft-server.readthedocs.io/)।

## 🛠️ সমস্যা সমাধান (Troubleshooting)

| সমস্যা | সমাধান |
|---|---|
| Space বিল্ড ফেইল হচ্ছে | Logs ট্যাবে এরর চেক করুন; সাধারণত নেটওয়ার্ক টাইমআউট হলে শুধু **Restart** দিলেই ঠিক হয়ে যায় |
| playit.gg এজেন্ট Offline দেখাচ্ছে | `PLAYIT_SECRET_KEY` Secret ঠিকভাবে সেট আছে কিনা এবং Space রিস্টার্ট করা হয়েছে কিনা চেক করুন |
| গেমে ল্যাগ হচ্ছে | Hugging Face Space-এর Hardware টায়ার বাড়ান, অথবা `VIEW_DISTANCE`/`SIMULATION_DISTANCE` আরও কমান |
| বন্ধুরা জয়েন করতে পারছে না | playit.gg ড্যাশবোর্ডে tunnel টির ঠিকানা আবার চেক করুন, এবং সার্ভার লগে `Done!` মেসেজ এসেছে কিনা নিশ্চিত করুন |

---

Made for a small group of friends to play **Minecraft (Purpur)** together — hosted on **Hugging Face Spaces**, exposed via **playit.gg**.
