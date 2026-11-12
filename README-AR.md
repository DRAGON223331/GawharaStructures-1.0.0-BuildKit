# GawharaStructures 1.0.0

بلجن Paper يضيف هياكل استكشاف وكنوز مخفية إلى عوالم GawharaSMP. الهياكل تُنشأ تلقائيًا عند توليد الشنكات الجديدة، وتحتوي على صناديق بغنائم مختلفة حسب البُعد.

## العوالم المسموح بها — حماية صريحة

التوليد مسموح فقط في أسماء العوالم التالية:

- `world` — أطلال ومعابد وأبراج مراقبة.
- `world_nether` — خزائن الروح ومخابئ الماجما.
- `world_the_end` — أضرحة الفراغ ومراصد الإندر.

**عالم `spawn` محظور داخل الكود نفسه**، وليس فقط من خلال `config.yml`. وأي عالم آخر لن يتولد فيه شيء. حتى أمر الإدارة اليدوي `/gstructures generate` يرفض العمل في `spawn`، ويرفض إنشاء هيكل من بُعد غير مناسب.

## الهياكل الموجودة

| العالم | الهيكل | المحتوى |
|---|---|---|
| `world` | Forest Shrine | معبد غابة وصندوق نادر |
| `world` | Ruined Watchtower | برج مراقبة مهجور وصندوق عادي |
| `world` | Buried Ruins | أطلال حجرية وصندوق نادر |
| `world_nether` | Soul Vault | خزنة من البلاكستون وصندوق نادر |
| `world_nether` | Magma Cache | مخبأ بازلت وماجما وصندوق عادي |
| `world_the_end` | Void Shrine | ضريح إند وصندوق نادر |
| `world_the_end` | Ender Observatory | مرصد إندر وصندوق عادي |

الغنائم قابلة للتعديل بالكامل من `loot-tables` في `config.yml`، وتشمل أوزان احتمالات الظهور والكميات.

## المتطلبات

- Paper `26.3.build.170-beta` (نفس خط إصدار السيرفر المستهدف).
- Java 25.
- Maven 3.9+ للبناء المحلي.
- لا يحتاج Vault أو أي بلجن إضافي.

## بناء ملف JAR

من مجلد المشروع:

```bash
mvn -B clean package
```

الملف الناتج:

`target/GawharaStructures-1.0.0.jar`

### بناء تلقائي عبر GitHub Actions

1. أنشئ Repository جديدًا على GitHub.
2. ارفع **محتويات هذا المشروع** إلى جذر المستودع، بما فيها مجلد `.github`.
3. افتح تبويب **Actions**.
4. شغّل Workflow باسم **Build GawharaStructures**.
5. بعد نجاحه، افتح نتيجة التشغيل ونزّل Artifact باسم `GawharaStructures-1.0.0`؛ ستجد بداخله ملف JAR.

الـ Workflow يستخدم JDK 25 ويجلب Paper API المطابقة من مستودع Paper الرسمي، ثم ينفذ Maven Build حقيقيًا.

## التثبيت

1. ضع `GawharaStructures-1.0.0.jar` داخل `plugins/`.
2. أعد تشغيل السيرفر بالكامل.
3. راجع `plugins/GawharaStructures/config.yml`.
4. خذ نسخة احتياطية قبل تجربة التوليد على عالمك الأساسي.

## أوامر اللاعبين

- `/gstructures locate` — يحدد أقرب هيكل مسجل في العالم الحالي.
- `/gstructures list` — يعرض أنواع الهياكل.
- `/gstructures help` — يعرض المساعدة.

## أوامر الإدارة

- `/gstructures generate <type>` — يولد هيكلًا يدويًا في الشنك الذي تقف فيه، في العالم المسموح فقط، وبعيدًا عن Spawn.
- `/gstructures reload` — يعيد تحميل الإعدادات ويحدّث إعدادات Populator للعوالم المفتوحة.

الصلاحية: `gawharastructures.admin` (افتراضيًا OP).

## التوليد التلقائي

- التوليد يحدث عند إنشاء الشنك لأول مرة فقط.
- الشنكات التي سبق استكشافها لن تتغير تلقائيًا؛ استكشف مناطق غير مكتشفة لرؤية الهياكل الجديدة.
- كل عالم له نسبة مستقلة من `chance-per-chunk`.
- يوجد حد أدنى للمسافة عن Spawn وحد أدنى بين الهياكل.
- التوليد يستخدم Paper `BlockPopulator` الحديث و`LimitedRegion`، ولا يعدّل العالم عبر `World` من خيط التوليد.

## أهم إعدادات config.yml

- `generation.enabled`: تشغيل/إيقاف التوليد التلقائي.
- `worlds.<world>.enabled`: تفعيل العالم المحدد، مع بقاء قائمة العوالم المسموحة الصريحة في الكود.
- `worlds.<world>.chance-per-chunk`: نسبة محاولة التوليد لكل شنك جديد.
- `worlds.<world>.structures`: أنواع الهياكل المسموح بها في العالم.
- `generation.min-distance-from-spawn`: المسافة الآمنة من نقطة Spawn لكل عالم.
- `generation.min-distance-between-structures`: المسافة الدنيا بين الهياكل.
- `generation.require-flat-ground` و`generation.max-ground-variation`: شروط الأرض المناسبة.
- `loot.items-per-chest` و`loot-tables`: عدد العناصر ومحتويات الصناديق وأوزانها.

## حدود الاختبار

تم التحقق من بناء ملفات المشروع، وصحة YAML، وقائمة العوالم، ومنع `spawn` في الكود بما في ذلك الأمر الإداري. لم يتم تنفيذ Maven Build فعلي داخل بيئة الإنشاء الحالية لعدم توفر Maven وJDK 25 أو اتصال بمستودع Paper؛ استخدم GitHub Actions أو بيئة بناء مجهزة لإنتاج JAR قبل تثبيته.
