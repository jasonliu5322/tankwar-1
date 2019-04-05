import com.sun.javafx.application.PlatformImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class TankWar extends JComponent {
    private static final long serialVersionUID = -6766726706227546163L;

    static final int WIDTH = 800, HEIGHT = 600;

    private static final int INIT_ENEMY_TANK_ROWS = 3;

    private static final int INIT_ENEMY_TANK_COUNT = 12;

    private static final int REPAINT_INTERVAL = 50;

    private Tank tank;
    private Blood blood;
    private List<Tank> enemyTanks;
    private List<Missile> missiles;
    private List<Explode> explodes;
    private final List<Wall> walls;
    private final AtomicInteger enemiesKilled = new AtomicInteger();
    private boolean petCried;

    private TankWar() {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setDoubleBuffered(true);
        this.walls = Arrays.asList(
            new Wall(250, 100, 300, 28),
            new Wall(100, 200, 28, 280),
            new Wall(670, 200, 28, 280),
            new Wall(250, 520, 300, 28)
        );
        this.init();
    }

    private void init() {
        this.tank = new Tank(WIDTH / 2, 50);
        this.enemyTanks = new CopyOnWriteArrayList<>();
        this.initEnemyTanks();
        this.missiles = new CopyOnWriteArrayList<>();
        this.explodes = new CopyOnWriteArrayList<>();
        this.blood = new Blood();
        this.petCried = false;
    }

    /**
     * Detect whether a given, certain tank is collided with other game objects
     * such as Walls, enemy tanks or the player tank
     */
    boolean isCollidedWithOtherObjects(Tank t) {
        for (Wall wall : walls) {
            if (t.isCollidedWith(wall)) {
                return true;
            }
        }

        for (Tank enemyTank : enemyTanks) {
            if (t.isCollidedWith(enemyTank)) {
                return true;
            }
        }

        return t.isCollidedWith(tank);
    }

    void addMissile(Missile missile) {
        this.missiles.add(missile);
    }

    private void initEnemyTanks() {
        for (int i = 0; i < INIT_ENEMY_TANK_ROWS; i++) {
            for (int j = 0; j < INIT_ENEMY_TANK_COUNT / INIT_ENEMY_TANK_ROWS; j++) {
                int x = 100 + 110 * (j + 1);
                int y = 300 + i * 50;
                Tank tank = new Tank(x, y, true, Direction.Up);
                // Game might be restarted many times and regenerated enemy tank might collide with player tank
                if (tank.isCollidedWith(this.tank)) {
                    continue;
                }
                this.enemyTanks.add(tank);
            }
        }
    }

    void restart() {
        this.enemiesKilled.set(0);
        this.init();
    }

    private boolean started;

    void startGame() {
        if (started) return;
        start();
        started = true;
    }

    private void start() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                //noinspection InfiniteLoopStatement
                while (true) {
                    try {
                        repaint();
                        triggerEvent();
                        Tools.sleepSilently(REPAINT_INTERVAL);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.execute();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!tank.isLive()) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            g.setColor(Color.RED);
            g.setFont(new Font("Default", Font.BOLD, 100));
            g.drawString("GAME OVER", 80, HEIGHT / 2 - 40);

            g.setColor(Color.WHITE);
            g.setFont(new Font("Default", Font.BOLD, 50));
            g.drawString("Press F2 to Start", 180, HEIGHT / 2 + 60);
        } else {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            g.setColor(Color.WHITE);
            g.setFont(new Font("Default", Font.BOLD, 14));
            g.drawString("Missiles: " + missiles.size(), 10, 50);
            g.drawString("Explodes: " + explodes.size(), 10, 70);
            g.drawString("Our Tank HP: " + tank.getHp(), 10, 90);
            g.drawString("Enemies Left: " + enemyTanks.size(), 10, 110);
            g.drawString("Enemies Killed: " + enemiesKilled, 10, 130);

            Image tree = Tools.getImage("tree.png");
            g.drawImage(tree, 12, HEIGHT - tree.getHeight(null) - 40, null);
            g.drawImage(tree, WIDTH - tree.getWidth(null) - 20, 12, null);

            this.drawGameObjects(missiles, g);
            this.drawGameObjects(explodes, g);
            this.drawGameObjects(enemyTanks, g);
            this.drawGameObjects(walls, g);

            this.drawGameObject(tank, g);
            this.drawGameObject(blood, g);
        }
    }

    private <T extends GameObject> void drawGameObjects(List<T> objects, Graphics g) {
        objects.forEach(o -> drawGameObject(o, g));
    }

    private <T extends GameObject> void drawGameObject(T obj, Graphics g) {
        if (obj != null && obj.isLive()) {
            obj.draw(g);
        }
    }

    private void triggerEvent() {
        if (!tank.isLive()) return;

        if (enemyTanks.isEmpty()) {
            this.initEnemyTanks();
        }

        missiles.removeIf(m -> !m.isLive());
        for (Missile m : missiles) {
            if (m.hitTanks(enemyTanks) || m.hitTank(tank)) {
                this.explodes.add(new Explode(m.x, m.y));
            } else {
                m.hitWalls(walls);
            }
        }

        int count = enemyTanks.size();
        enemyTanks.removeIf(e -> !e.isLive());
        enemiesKilled.addAndGet(count - enemyTanks.size());
        enemyTanks.forEach(Tank::actRandomly);

        if (tank.isDying()) {
            // Your loyal pet would probably cry for you, dude
            if (Tools.nextInt(10) < 2 && !petCried) {
                Tools.playAudio("camel.mp3");
                petCried = true;
            }
            this.blood.setLive(Tools.nextInt(4) < 3);
        }

        if (tank.isCollidedWith(blood)) {
            tank.revive();
            Tools.playAudio("revive.wav");
            blood.setLive(false);
        }

        explodes.removeIf(e -> !e.isLive());
    }

    private static TankWar INSTANCE;

    /**
     * Singleton with lazy initialization
     */
    static TankWar getInstance() {
        if (INSTANCE == null)
            INSTANCE = new TankWar();
        return INSTANCE;
    }

    public static void main(String[] args) {
        PlatformImpl.startup(() -> {});
        Tools.setTheme();
        JFrame frame = new JFrame("The Most Boring Tank War Game");
        frame.setIconImage(Tools.getImage("icon.png"));
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        TankWar tankWar = TankWar.getInstance();
        frame.add(tankWar);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                tankWar.tank.keyPressed(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                tankWar.tank.keyReleased(e);
            }
        });
        frame.setResizable(false);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

}