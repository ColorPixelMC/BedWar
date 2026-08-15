package org.sobadfish.bedwar.panel.items;


import cn.nukkit.item.Item;
import lombok.Getter;
import lombok.Setter;
import org.sobadfish.bedwar.item.config.NbtItemInfoConfig;
import org.sobadfish.bedwar.item.nbt.INbtItem;

/**
 * @author SoBadFish
 * 2022/1/5
 */
public class NbtDefaultItem extends DefaultItem{

    public INbtItem item;


    private NbtItemInfoConfig playerItem;

    public void setItem(INbtItem item) {
        this.item = item;
    }

    public NbtItemInfoConfig getPlayerItem() {
        return playerItem;
    }

    public void setPlayerItem(NbtItemInfoConfig playerItem) {
        this.playerItem = playerItem;
    }

    NbtDefaultItem(INbtItem item, String moneyItem, int count) {
        super(null, moneyItem, count);
        this.item = item;
    }





    @Override
    public Item[] getItem() {
        return new Item[]{playerItem.getItem()};
    }


}
