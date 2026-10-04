# 🧭 NPC Navs (PolyNav Core)
> **Високопродуктивний автономний навігаційний рушій (Standalone NavMesh Engine) для Fabric 1.20.1**

`NPC Navs` — це автономна модульна бібліотека навігації для Minecraft, розроблена спеціально для симуляції десятків і сотень розумних NPC/мобів без просідання TPS сервера. Вона замінює важкий та незграбний ванільний пошук шляхів на сучасну систему навігаційних полігональних сіток (NavMesh), аналогічну до Unity NavMesh / Unreal Navigation System.

---

## ⚡ Ключові можливості рушія

1. **Суб-блочна роздільність 0.125 (1/8 блоку):**
   - Повна підтримка складних воксельних геометрій: сходи, плити, снігові та вертикальні шари архітектурних модів (зокрема **Conquest Reforged**).
   - Жодних хибних колізій тіла чи голови при підйомі на сходинки.

2. **Асинхронний A\* (Off-thread Pathfinding):**
   - Пошук шляху графом виконується у фоновому пулі потоків (`AsyncPathProcessor`) за межами основного тіку сервера.
   - 30–50+ активних жителів у місті споживають **< 0.5 мс на тік** (менше 1% бюджету 20 TPS сервера).

3. **Hitbox-перевірене згладжування Безьє (Bezier Fillet):**
   - Двоетапний оптимізатор шляху: Funnel-скорочення + вписування квадратичних параболічних дуг Безьє на поворотах.
   - Кожна точка дуги перевіряється повноцінним AABB-хітбоксом агента ($r=0.35$ м, $h=1.80$ м) проти вокселів стін, тому NPC повертають за плавною «гоночною траєкторією» і **ніколи не врізаються плечем у кути**.

4. **Інтелект проходження дверей:**
   - **2-Block Runway**: алгоритм будує строго пряму лінію заходу та виходу з дверей без кутових зрізань.
   - **Door Leaf Offset**: динамічне зміщення траєкторії на 0.10 м від завіси дверей у вільний просвіт.
   - **State Machine без спаму**: бот відчиняє двері при наближенні, фіксує перетин прорізу і зачиняє за собою лише після виходу на інший бік. Жодного спаму звуків 20 разів на секунду.

5. **Розділення кроку та стрибка (Smooth Step vs Jump):**
   - Плавне сходження на сходи, плити та шари без підстрибувань (`stepHeight = 0.6f`).
   - Навмисний реалістичний стрибок виконується **тільки** при штурмі цільного 1.0-метрового блоку.

6. **Строге уникнення води:**
   - Калюжі, річки та водойми за замовчуванням маркуються як непрохідні (`allowWater = false`), змушуючи ботів обирати сухий обхідний маршрут.

7. **Клієнтський 3D неоновий рендерер:**
   - Безперервна лінія маршруту без лагів і сміття з часток:
     - 🟢 **Зелений**: уже пройдена ділянка шляху.
     - 🟠 **Помаранчевий**: зона дверей.
     - 🔵 **Блакитний**: активний запланований маршрут.

---

## 📦 Як підключити у свій мод

Модуль опубліковано для використання як залежність Fabric-моду.

### 1. `build.gradle` вашого моду:

```groovy
repositories {
    mavenLocal() // якщо збираєте локально
}

dependencies {
    // Підключення NavMesh рушія
    modImplementation "ua.stubname:stubname:0.1.0"
    include "ua.stubname:stubname:0.1.0" // опціонально: ji-jar пакування
}
```

### 2. `fabric.mod.json` вашого моду:

```json
{
  "depends": {
    "stubname": ">=0.1.0"
  }
}
```

---

## 🚀 Швидкий старт: створення власного агента

Будь-який кастомний моб або NPC може стати повноцінним агентом рушія всього за кілька рядків коду:

```java
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;
import ua.stubname.api.INavMeshAgent;
import ua.stubname.entity.NavMeshMoveControl;
import ua.stubname.entity.NavMeshNavigation;
import ua.stubname.navmesh.pathfinding.NavPath;
import ua.stubname.network.PathNetwork;

public class MyCustomNpc extends PathAwareEntity implements INavMeshAgent {
    private NavPath currentPath;

    public MyCustomNpc(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        // 1. Підключаємо розумні контролери
        this.moveControl = new NavMeshMoveControl(this);
        this.navigation = new NavMeshNavigation(this, world);
        
        // 2. Дозволяємо плавний підйом на сходинки й плити
        this.setStepHeight(0.6f);
    }

    @Override
    public NavPath getCurrentNavPath() {
        return currentPath;
    }

    @Override
    public void setCurrentNavPath(NavPath path) {
        this.currentPath = path;
        // Відправляємо клієнтам для 3D неонового рендерингу лінії
        PathNetwork.sendPathToClients(this, path);
    }
}
```

### Командування агентом через фасад `NavMeshAPI`:

```java
import ua.stubname.api.NavMeshAPI;

// 1. Відправити бота в точку (асинхронно прокладе шлях і почне рух)
NavMeshAPI.navigateTo(myNpc, new Vec3d(100.5, 64.0, -250.5), 0.28);

// 2. Або просто отримати розрахований шлях для власного AI:
NavMeshAPI.findPathAsync(serverWorld, startPos, goalPos).thenAccept(path -> {
    if (path != null) {
        System.out.println("Знайдено шлях з точок: " + path.getPoints().size());
    }
});
```

---

## 🛠️ Внутрішньоігрові інструменти та команди

Модуль містить готові команди для адмінів та левел-дизайнерів:

| Команда | Опис |
| :--- | :--- |
| `/navmesh wand` | Видає чарівну паличку для виділення зони (ЛКМ — Pos1, ПКМ — Pos2). |
| `/navmesh bake <name>` | Запікає NavMesh для виділеної зони з урахуванням суб-блоків та дверей. |
| `/navmesh save <name>` | Зберігає запечену сітку в папку світу `navmesh/<name>.dat`. |
| `/navmesh list` | Показує список завантажених сіток та кількість вузлів. |
| `/navmesh debug path <on\|off>` | Вмикає/вимикає серверну візуалізацію точок. |
| `/npc spawn` | Спавнить тестового демонстраційного NPC на вашій позиції. |
| `/npc goto <x> <y> <z>` | Відправляє найближчого NPC у вказані координати. |
| `/npc stop` | Зупиняє рух найближчого NPC. |

---

## ⚙️ Конфігурація (`config/stubname.json`)

Файл створюється автоматично при першому запуску:

```json
{
  "agentRadius": 0.35,
  "agentHeight": 1.8,
  "maxStepHeight": 0.6,
  "maxDropHeight": 3.0,
  "allowWater": false,
  "nodeResolution": 0.5,
  "costs": {
    "dirt_path": 0.5,
    "stone": 0.8,
    "grass_block": 1.0,
    "water": 1000.0
  }
}
```

---

## 🏛️ Архітектура пакетів

- **`ua.stubname.api`**: Чистий публічний інтерфейс для інтеграції іншими модами (`NavMeshAPI`, `INavMeshAgent`).
- **`ua.stubname.navmesh.baker`**: Сканер воксельного світу та генератор графу NavMesh з урахуванням сходів і дверей.
- **`ua.stubname.navmesh.pathfinding`**: Асинхронний процесор A\*, воксельний Funnel та скруглювач Безьє.
- **`ua.stubname.navmesh.storage`**: Бінарна серіалізація та швидке кешування сітки чанками.
- **`ua.stubname.entity`**: Реалізація контролерів `NavMeshMoveControl`, `NavMeshNavigation` та референсна сутність `NpcEntity`.
- **`ua.stubname.client`**: Клієнтська частина та 3D OpenGL лінійний рендерер.
