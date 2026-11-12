# GawharaStructures 1.1.0 — GawharaCoins Integration

كل لاعب يقتل Warden في عوالم Survival المسموحة يحصل على **100 GC** افتراضيًا. تُعدل من `coins.warden-kill`. المكافأة تُربط بمعرّف UUID الخاص بالـ Warden حتى لا تتكرر.

توليد الهياكل ما زال محصورًا في `world`, `world_nether`, `world_the_end`، ويُمنع في `spawn`. لا يتم تغيير آلية `BlockPopulator` الحالية.

البناء: Java 25 + Paper 26.3 build 170-beta. ثبّت GawharaCoins أولًا.
