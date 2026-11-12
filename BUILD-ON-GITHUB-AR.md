# بناء GawharaStructures على GitHub

1. ارفع محتويات هذا المجلد إلى Repository.
2. تأكد من وجود `.github/workflows/main.yml`.
3. افتح Actions وشغّل `Build GawharaStructures`.
4. بعد النجاح حمّل Artifact باسم `GawharaStructures-JAR`.

المتطلبات: JDK 25 وPaper API 26.3 build 170-beta. لا ترفع مجلد `target` إلى GitHub؛ workflow يبنيه تلقائيًا.
