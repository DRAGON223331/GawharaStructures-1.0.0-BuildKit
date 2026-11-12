## إصلاح الكراش في Paper 26.3 (1.0.2)

كان استدعاء فحص استواء الأرض يمرر معاملات `isFlat` بترتيب خاطئ: كان يرسل إحداثي X مكان ارتفاع الأرض، ما تسبب في استدعاء `LimitedRegion.getHighestBlockYAt` خارج المنطقة المسموح بها (`Coordinates ... are not in the region`) وأوقف Chunk Scheduler.

تم تصحيح ترتيب المعاملات، وإضافة `LimitedRegion.isInRegion` قبل كل قراءة ارتفاع في فحص الاستواء، وكذلك التحقق من نقطة مركز الشنك قبل فحص سطح الأرض.

**مهم:** أزل JAR 1.0.1 قبل التشغيل، وثبّت 1.0.2 بعد إيقاف السيرفر بالكامل. لا تستخدم GawharaPluginManager لتحديث نسخة بلجن محمّلة بالفعل؛ تحميل نسخة ثانية فوق نفس البلجن غير آمن.

# GawharaStructures 1.0.2 — Paper 26.3

## إصلاح مهم في 1.0.2
- إصلاح ترتيب معاملات `isFlat` الذي كان يمرر X بدل ارتفاع الأرض، ويسبب قراءة إحداثيات خارج `LimitedRegion`.
- إضافة تحقق `LimitedRegion.isInRegion` قبل كل قراءة ارتفاع داخل فحص استواء الأرض.
- إضافة تحقق حدود قبل قراءة سطح الأرض في بداية التوليد.
- هذا الإصدار يمنع خطأ `Coordinates ... are not in the region` الذي كان يوقف Chunk Scheduler في Paper 26.3.

## العوالم
التوليد محصور في `world` و`world_nether` و`world_the_end` فقط. عالم `spawn` محظور.

## البناء على GitHub
1. ارفع محتويات المشروع إلى Repository.
2. افتح Actions وشغّل `Build GawharaStructures`.
3. نزّل Artifact `GawharaStructures-1.0.2` بعد نجاح البناء.

## قبل التحديث
- أوقف السيرفر بالكامل.
- أزل JAR القديم `GawharaStructures-1.0.1.jar`.
- ضع JAR الجديد 1.0.2.
- لا تحذف `plugins/GawharaStructures/structures.yml` أو `config.yml`.
- شغّل السيرفر.

لا تستخدم PluginManager لمحاولة تحميل نسخة ثانية فوق النسخة المحمّلة؛ تحديث كود Plugin جارٍ يحتاج إعادة تشغيل كاملة.
