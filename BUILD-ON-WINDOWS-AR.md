# استخراج JAR على Windows

## المتطلبات
- JDK 25 (وليس JRE فقط).
- Maven 3.9 أو أحدث.
- اتصال إنترنت أول مرة لتنزيل Paper API واعتماديات Maven.

## الخطوات
1. فك ضغط المشروع كاملًا.
2. ثبّت JDK 25، ثم تأكد أن `java -version` يعرض الإصدار 25.
3. ثبّت Maven، ثم تأكد أن `mvn -version` يعمل.
4. افتح مجلد `GawharaStructures-1.0.0`.
5. شغّل `Build-JAR.bat`.
6. بعد نجاح البناء ستجد الملف هنا:
   `target/GawharaStructures-1.0.0.jar`

السكربت يتوقف برسالة واضحة إذا كان Java أو Maven غير متوفر أو الإصدار غير مناسب.

## بديل GitHub Actions
ارفع محتويات مجلد المشروع إلى جذر Repository، ثم افتح Actions وشغّل `Build GawharaStructures`. حمّل Artifact الناتج بعد نجاح البناء.
