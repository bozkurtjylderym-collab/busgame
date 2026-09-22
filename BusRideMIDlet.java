import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.media.Manager;
import java.util.Random;

public class BusRideMIDlet extends MIDlet implements CommandListener {
    Display display; BusCanvas cv; TextBox tb; int ni, pc;
    String[] names = new String[4];
    Command ok = new Command("OK", Command.OK, 1);
    public void startApp() { display = Display.getDisplay(this); cv = new BusCanvas(this); display.setCurrent(cv); }
    public void pauseApp() {}
    public void destroyApp(boolean u) {}
    void newGame(int count) { pc = count; ni = 0; askName(); }
    void askName() {
        tb = new TextBox("Игрок " + (ni + 1), "Игрок " + (ni + 1), 12, TextField.ANY);
        tb.addCommand(ok); tb.setCommandListener(this); display.setCurrent(tb);
    }
    public void commandAction(Command c, Displayable d) {
        if (d == tb) {
            String s = tb.getString().trim();
            if (s.length() == 0) s = "Игрок " + (ni + 1);
            names[ni] = s; ni++;
            if (ni < pc) askName(); else { cv.start(names, pc); display.setCurrent(cv); }
        }
    }
}

class BusCanvas extends Canvas {
    static final int TITLE=0,PC=1,HAND=2,BET=3,G1=4,G2=5,G3=6,G4=7,REV=8,CHOICE=9,LOST=10,WON=11,STAND=12,OVER=13,RULES=14;
    static final int[] MULT = {2,4,8,20};
    static final String[] RANKS = {"2","3","4","5","6","7","8","9","10","J","Q","K","A"};
    BusRideMIDlet mid; Random rnd = new Random();
    int state = TITLE, pc, cur, bet, drawn, multIdx, nextSt, lastWin, guess;
    int[] bal = new int[4]; boolean[] alive = new boolean[4];
    String[] names = new String[4];
    int[] deck = new int[52]; int[] cards = new int[4];
    int[][] z = new int[12][5]; int zn;
    Graphics pg;
    String msg = "";

    BusCanvas(BusRideMIDlet m) { mid = m; }
    void start(String[] n, int count) {
        pc = count;
        for (int i = 0; i < pc; i++) { names[i] = n[i]; bal[i] = 500; alive[i] = true; }
        cur = 0; state = HAND;
    }
    int rank(int c) { return c >> 2; }
    int suit(int c) { return c & 3; }
    boolean red(int c) { int s = suit(c); return s == 1 || s == 2; }
    void shuffle() { for (int i = 0; i < 52; i++) deck[i] = i;
        for (int i = 51; i > 0; i--) { int j = rnd.nextInt(i + 1); int t = deck[i]; deck[i] = deck[j]; deck[j] = t; } }
    int aliveCount() { int n = 0; for (int i = 0; i < pc; i++) if (alive[i]) n++; return n; }
    void beep(int note) { try { Manager.playTone(note, 250, 90); } catch (Exception e) {} }

    protected void keyPressed(int k) {
        int d = -1;
        if (k >= '0' && k <= '9') d = k - '0';
        int ga = getGameAction(k);
        if (ga == UP) d = 2; if (ga == DOWN) d = 8;
        if (ga == LEFT) d = 4; if (ga == RIGHT) d = 6; if (ga == FIRE) d = 5;
        if (d >= 0) act(d);
    }
    protected void pointerPressed(int x, int y) {
        for (int i = 0; i < zn; i++)
            if (x >= z[i][0] && x < z[i][0]+z[i][2] && y >= z[i][1] && y < z[i][1]+z[i][3]) { act(z[i][4]); return; }
    }

    void act(int d) {
        switch (state) {
            case TITLE: if (d == 5) state = PC; if (d == 6) state = RULES; break;
            case PC: if (d >= 2 && d <= 4) mid.newGame(d); break;
            case RULES: if (d == 5) state = TITLE; break;
            case HAND: if (d == 5) { bet = Math.min(100, bal[cur]); state = BET; } break;
            case BET:
                if (d == 2) bet = Math.min(bal[cur], bet + 25);
                if (d == 8) bet = Math.max(10, bet - 25);
                if (d == 6) bet = Math.min(bal[cur], bet + 100);
                if (d == 4) bet = Math.max(10, bet - 100);
                if (d == 3) bet = Math.min(bal[cur], bet * 2);
                if (d == 9) bet = bal[cur];
                if (d == 5 && bet >= 10) { bal[cur] -= bet; shuffle(); drawn = 0; state = G1; }
                break;
            case G1: case G2: case G3: case G4: {
                int round = state - G1;
                guess = d;
                cards[drawn] = deck[drawn]; drawn++;
                boolean win = false;
                if (round == 0) win = (d == 4) == red(cards[0]);
                if (round == 1) win = (d == 4) == (rank(cards[1]) >= rank(cards[0]));
                if (round == 2) { int lo = Math.min(rank(cards[0]), rank(cards[1])), hi = Math.max(rank(cards[0]), rank(cards[1]));
                    int v = rank(cards[2]); boolean in = v > lo && v < hi, out = v < lo || v > hi; win = (d == 4) ? in : out; }
                if (round == 3) win = suit(cards[3]) == suitOfKey(d);
                if (win) {
                    beep(72);
                    if (round == 3) { lastWin = bet * 20; bal[cur] += lastWin; nextSt = WON; msg = "МАСТЬ УГАДАНА! x20"; }
                    else { multIdx = round; nextSt = CHOICE; msg = "Верно! Множитель x" + MULT[round]; }
                } else { nextSt = LOST; msg = "Мимо! Автобус уехал..."; beep(45); }
                state = REV;
                break;
            }
            case REV: if (d == 5) { state = nextSt;
                if (state == LOST) { mid.display.vibrate(300); if (bal[cur] <= 0) alive[cur] = false; } } break;
            case CHOICE:
                if (d == 4) { lastWin = bet * MULT[multIdx]; bal[cur] += lastWin; state = WON; beep(76); }
                if (d == 6) state = G1 + multIdx + 1;
                break;
            case WON: case LOST: if (d == 5) state = STAND; break;
            case STAND:
                if (d == 0) state = OVER;
                if (d == 5) { if (aliveCount() <= 1) state = OVER;
                    else { for (int i = 1; i <= pc; i++) { int idx = (cur + i) % pc; if (alive[idx]) { cur = idx; break; } } state = HAND; } }
                break;
            case OVER: if (d == 5) state = TITLE; break;
        }
        repaint();
    }
    int suitOfKey(int d) { return d == 1 ? 0 : d == 3 ? 1 : d == 7 ? 2 : 3; }

    void btn(int x, int y, int w, int h, String label, int key) {
        Graphics g = pg;
        g.setColor(0x334455); g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(0xFFFFFF); g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
        g.drawString(label, x + w/2, y + h/2, Graphics.HCENTER | Graphics.VCENTER);
        z[zn][0]=x; z[zn][1]=y; z[zn][2]=w; z[zn][3]=h; z[zn][4]=key; zn++;
    }
    void drawSuit(Graphics g, int s, int cx, int cy, int r, boolean redC) {
        g.setColor(redC ? 0xCC0000 : 0x111111);
        if (s == 1) { g.fillArc(cx-r, cy-r, r, r, 0, 360); g.fillArc(cx, cy-r, r, r, 0, 360);
            g.fillTriangle(cx-r, cy-r/2, cx+r, cy-r/2, cx, cy+r); }
        if (s == 2) { g.fillTriangle(cx, cy-r, cx+r, cy, cx, cy+r); g.fillTriangle(cx, cy-r, cx-r, cy, cx, cy+r); }
        if (s == 0) { g.fillTriangle(cx, cy-r, cx-r, cy, cx+r, cy); g.fillArc(cx-r, cy-r/2, r, r, 0, 360);
            g.fillArc(cx, cy-r/2, r, r, 0, 360); g.fillRect(cx-r/4, cy, r/2, r); }
        if (s == 3) { g.fillArc(cx-r/2, cy-r, r, r, 0, 360); g.fillArc(cx-r, cy-r/2, r, r, 0, 360);
            g.fillArc(cx, cy-r/2, r, r, 0, 360); g.fillRect(cx-r/4, cy, r/2, r); }
    }
    void drawCard(Graphics g, int c, int x, int y, int w, int h) {
        g.setColor(0xFFFFFF); g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(0x888888); g.drawRoundRect(x, y, w, h, 6, 6);
        boolean r = red(c);
        g.setColor(r ? 0xCC0000 : 0x111111);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        g.drawString(RANKS[rank(c)], x + 4, y + 2, Graphics.LEFT | Graphics.TOP);
        drawSuit(g, suit(c), x + w/2, y + h/2 + 6, w/5, r);
    }

    protected void paint(Graphics g) {
        zn = 0; pg = g;
        g.setColor(0x0E1A24); g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(0x7FD4C1); g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        g.drawString("RIDE THE BUS", 120, 4, Graphics.HCENTER | Graphics.TOP);
        switch (state) {
            case TITLE:
                g.setColor(0xFFFFFF); g.drawString("Казино Schedule I", 120, 90, Graphics.HCENTER | Graphics.TOP);
                btn(40, 160, 160, 40, "НОВАЯ ИГРА [5]", 5);
                btn(40, 210, 160, 40, "ПРАВИЛА [6]", 6); break;
            case PC:
                g.setColor(0xFFFFFF); g.drawString("Сколько игроков?", 120, 60, Graphics.HCENTER | Graphics.TOP);
                btn(40, 120, 160, 36, "2 ИГРОКА [2]", 2);
                btn(40, 165, 160, 36, "3 ИГРОКА [3]", 3);
                btn(40, 210, 160, 36, "4 ИГРОКА [4]", 4); break;
            case RULES: {
                String[] L = {"1: Красная/чёрная -> x2","2: Выше-равна/ниже -> x4","3: Внутри/снаружи -> x8",
                    "4: Угадай масть -> x20","После раунда: забрать или риск.","Ошибка = ставка сгорает.",
                    "Равенство в р.2 = выше.","Граница в р.3 = проигрыш.","[5] Назад"};
                g.setColor(0xFFFFFF); g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
                for (int i = 0; i < L.length; i++) g.drawString(L[i], 12, 40 + i * 26, Graphics.LEFT | Graphics.TOP);
                break; }
            case HAND:
                g.setColor(0xFFFFFF); g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
                g.drawString(names[cur], 120, 110, Graphics.HCENTER | Graphics.TOP);
                g.drawString("Передайте телефон", 120, 150, Graphics.HCENTER | Graphics.TOP);
                btn(60, 220, 120, 40, "OK [5]", 5); break;
            case BET:
                g.setColor(0xFFFFFF); g.drawString(names[cur] + ": " + bal[cur] + " фишек", 120, 30, Graphics.HCENTER | Graphics.TOP);
                g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
                g.drawString("СТАВКА: " + bet, 120, 60, Graphics.HCENTER | Graphics.TOP);
                btn(15, 110, 68, 34, "-100 [4]", 4); btn(86, 110, 68, 34, "-25 [8]", 8); btn(157, 110, 68, 34, "x2 [3]", 3);
                btn(15, 150, 68, 34, "+25 [2]", 2); btn(86, 150, 68, 34, "+100 [6]", 6); btn(157, 150, 68, 34, "ВСЁ [9]", 9);
                btn(40, 200, 160, 44, "ПОЕХАЛИ [5]", 5); break;
            case G1: case G2: case G3: case G4: {
                int round = state - G1;
                for (int i = 0; i < drawn; i++) drawCard(g, cards[i], 8 + i * 58, 30, 52, 72);
                g.setColor(0xFFFFFF); g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
                String q = round == 0 ? "Цвет карты?" : round == 1 ? "Выше или ниже " + RANKS[rank(cards[0])] + "?"
                        : round == 2 ? "Внутри " + RANKS[Math.min(rank(cards[0]),rank(cards[1]))] + "-" + RANKS[Math.max(rank(cards[0]),rank(cards[1]))] + "?" : "Точная масть?";
                g.drawString(q, 120, 120, Graphics.HCENTER | Graphics.TOP);
                g.drawString("Банк: " + (bet * MULT[round]), 120, 145, Graphics.HCENTER | Graphics.TOP);
                if (round < 3) {
                    String a = round == 0 ? "КРАСНАЯ [4]" : round == 1 ? "ВЫШЕ/РАВНО [4]" : "ВНУТРИ [4]";
                    String b = round == 0 ? "ЧЁРНАЯ [6]" : round == 1 ? "НИЖЕ [6]" : "СНАРУЖИ [6]";
                    btn(12, 240, 105, 44, a, 4); btn(123, 240, 105, 44, b, 6);
                } else {
                    btn(12, 200, 105, 40, "ПИКИ [1]", 1); btn(123, 200, 105, 40, "ЧЕРВИ [3]", 3);
                    btn(12, 246, 105, 40, "БУБНЫ [7]", 7); btn(123, 246, 105, 40, "ТРЕФЫ [9]", 9);
                }
                break; }
            case REV:
                drawCard(g, cards[drawn - 1], 93, 60, 54, 76);
                g.setColor(0xFFFFFF); g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
                g.drawString(msg, 120, 160, Graphics.HCENTER | Graphics.TOP);
                btn(60, 230, 120, 40, "ДАЛЬШЕ [5]", 5); break;
            case CHOICE:
                g.setColor(0xFFFFFF); g.drawString("Забрать " + (bet * MULT[multIdx]) + " или рискнуть?", 120, 120, Graphics.HCENTER | Graphics.TOP);
                btn(12, 200, 105, 44, "ЗАБРАТЬ [4]", 4); btn(123, 200, 105, 44, "РИСК [6]", 6); break;
            case WON: case LOST:
                g.setColor(state == WON ? 0x66DD66 : 0xDD5555);
                g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
                g.drawString(state == WON ? ("+" + lastWin) : ("-" + bet), 120, 100, Graphics.HCENTER | Graphics.TOP);
                g.setColor(0xFFFFFF); g.drawString(names[cur] + ": " + bal[cur], 120, 150, Graphics.HCENTER | Graphics.TOP);
                btn(60, 230, 120, 40, "ДАЛЬШЕ [5]", 5); break;
            case STAND:
                g.setColor(0xFFFFFF); g.drawString("ТАБЛО", 120, 30, Graphics.HCENTER | Graphics.TOP);
                for (int i = 0; i < pc; i++) {
                    g.setColor(alive[i] ? 0xFFFFFF : 0x777777);
                    g.drawString((i+1) + ". " + names[i] + (alive[i] ? "" : " (выбыл)") + ": " + bal[i], 20, 60 + i * 30, Graphics.LEFT | Graphics.TOP);
                }
                btn(20, 240, 95, 40, "ДАЛЬШЕ [5]", 5); btn(125, 240, 95, 40, "ФИНИШ [0]", 0); break;
            case OVER: {
                int w = 0; for (int i = 1; i < pc; i++) if (bal[i] > bal[w]) w = i;
                g.setColor(0xFFD75E); g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
                g.drawString("ПОБЕДИЛ " + names[w], 120, 90, Graphics.HCENTER | Graphics.TOP);
                g.setColor(0xFFFFFF); g.drawString("Банк: " + bal[w], 120, 130, Graphics.HCENTER | Graphics.TOP);
                btn(60, 230, 120, 40, "МЕНЮ [5]", 5); break; }
        }
    }
                  }
