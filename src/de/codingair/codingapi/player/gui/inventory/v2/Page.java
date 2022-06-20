package de.codingair.codingapi.player.gui.inventory.v2;

import de.codingair.codingapi.player.gui.inventory.v2.buttons.Button;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;

public abstract class Page {
    protected final GUI gui;
    protected final Page basic;
    private final HashMap<Integer, Button> items;
    private String title = null;

    public Page(GUI gui, Page basic) {
        this.gui = gui;
        this.basic = basic;
        this.items = new HashMap<>();
    }

    public abstract void buildItems();

    public void apply() {
        apply(true);
    }

    public void apply(boolean basic) {
        buildItems();
        deploy(basic);
    }

    private void deploy(boolean basic) {
        if(basic && this.basic != null) this.basic.apply(basic);
        items.forEach((slot, item) -> gui.setItem(slot, item.buildItem()));

        gui.updateTitle(this.title);
    }

    public void rebuild() {
        rebuild(true);
    }

    /**
     * Clears and rebuilds buttons & updates inventory
     * @param basic Predecessor page
     */
    public void rebuild(boolean basic) {
        clear(basic);
        items.clear();
        buildItems();
        deploy(basic);
    }

    public void updateItems() {
        updateItems(true);
    }

    public void updateItems(boolean basic) {
        deploy(basic);
    }

    public void clear() {
        clear(true);
    }

    public void clear(boolean basic) {
        gui.clear(items.keySet());
        if(basic && this.basic != null) this.basic.clear(basic);
    }

    public Button getButtonAt(int slot) {
        Button b = items.get(slot);

        if(b == null && this.basic != null) return this.basic.getButtonAt(slot);
        else return b;
    }

    public Button getButtonAt(int x, int y) {
        return getButtonAt(x + y * 9);
    }

    public Page(GUI gui) {
        this(gui, null);
    }

    public void addButton(int slot, Button button) {
        items.put(slot, button);
    }

    public void addButtonIfAbsent(int slot, Button button) {
        items.putIfAbsent(slot, button);
    }

    public void addButton(int x, int y, Button button) {
        items.put(x + y * 9, button);
    }

    public void addButtonIfAbsent(int x, int y, Button button) {
        items.putIfAbsent(x + y * 9, button);
    }

    public void addLine(int x0, int y0, int x1, int y1, Button button) {
        addLine(x0, y0, x1, y1, button, false);
    }

    public void addLine(int x0, int y0, int x1, int y1, Button button, boolean overwrite) {
        double cX = x0, cY = y0;
        Vector v = new Vector(x1, y1, 0).subtract(new Vector(x0, y0, 0)).normalize();

        do {
            if(overwrite) addButton((int) cX, (int) cY, button);
            else addButtonIfAbsent((int) cX, (int) cY, button);

            cX += v.getX();
            cY += v.getY();
        } while((int) cX != x1 || (int) cY != y1);

        if(overwrite) addButton((int) cX, (int) cY, button);
        else addButtonIfAbsent((int) cX, (int) cY, button);
    }

    public void destroy() {
        this.items.clear();
    }

    public Page getBasic() {
        return basic;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
