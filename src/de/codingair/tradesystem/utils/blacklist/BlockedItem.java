package de.codingair.tradesystem.utils.blacklist;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.Objects;

public class BlockedItem {
    private final Material material;
    private final String name;
    private final String lore;

    public BlockedItem(Material material, String name, String lore) {
        this.material = material;
        this.name = name;
        this.lore = lore;
    }

    public BlockedItem(Material material) {
        this(material, null, null);
    }
    
    public BlockedItem(String lore) {
        this(null, null, lore);
    }

    public static BlockedItem fromString(String s) {
        try {
            JSONObject json = (JSONObject) new JSONParser().parse(s);

            Material material = json.get("Material") == null ? null : Material.valueOf((String) json.get("Material"));
            String name = json.get("Displayname") == null ? null : (String) json.get("Displayname");
            String lore = json.get("Lore") == null ? null : (String) json.get("Lore");
            
            return new BlockedItem(material, name, lore);
        } catch (NoSuchFieldError | IllegalArgumentException ex) {
            return null;
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean matches(ItemStack item) {
        boolean matches = false;

        if (material != null) {
        	if (item.getType() == this.material) matches = true;
        }

        if (name != null && item.hasItemMeta() && item.getItemMeta().getDisplayName() != null) {
            if (item.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', name))) matches = true;
        }
        
        if (lore != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            if (item.getItemMeta().getLore().contains(ChatColor.translateAlternateColorCodes('&', lore))) matches = true;
        }

        return matches;
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }
    
    public String getLore() {
    	return lore;
    }

    @SuppressWarnings("unchecked")
	@Override
    public String toString() {
        JSONObject json = new JSONObject();

        if (this.material != null) {
            json.put("Material", this.material.name());
        }

        if (this.name != null) {
            json.put("Displayname", this.name);
        }
        
        if (this.lore != null) {
            json.put("Lore", this.lore);
        }

        return json.toJSONString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockedItem that = (BlockedItem) o;
        return material == that.material &&
                Objects.equals(name, that.name) &&
                Objects.equals(lore, that.lore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, name, lore);
    }
}
