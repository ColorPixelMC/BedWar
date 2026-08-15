package org.sobadfish.bedwar.entity;

import cn.nukkit.entity.projectile.EntitySnowball;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import lombok.Getter;
import lombok.Setter;
import org.sobadfish.bedwar.item.nbt.IDropItem;
import org.sobadfish.bedwar.player.PlayerInfo;

/**
 * @author Sobadfish
 * @date 2024/8/26
 */
public class BedWarEntitySnowBall extends EntitySnowball {


    private PlayerInfo master;

    public BedWarEntitySnowBall(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }


    private IDropItem usedItem;

    public PlayerInfo getMaster() {
        return master;
    }

    public void setMaster(PlayerInfo master) {
        this.master = master;
    }

    public IDropItem getUsedItem() {
        return usedItem;
    }

    public void setUsedItem(IDropItem usedItem) {
        this.usedItem = usedItem;
    }
}
