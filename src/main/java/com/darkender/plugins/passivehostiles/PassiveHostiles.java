package com.darkender.plugins.passivehostiles;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PassiveHostiles extends JavaPlugin implements Listener
{
    private NamespacedKey inLoveKey;
    private NamespacedKey lastInLoveKey;
    private Map<EntityType, Set<Mob>> mobsInLove;
    private Map<EntityType, Material> breedingItems;
    
    @Override
    public void onEnable()
    {
        inLoveKey = new NamespacedKey(this, "in-love");
        lastInLoveKey = new NamespacedKey(this, "last-in-love");
        mobsInLove = new HashMap<>();
        breedingItems = new HashMap<>();
        breedingItems.put(EntityType.CREEPER, Material.GUNPOWDER);
        
        getServer().getPluginManager().registerEvents(this, this);
        
        // Run this on a timer to interrupt hostile mob targeting when a player
        // switches to the breeding item *after* the mob started targeting
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable()
        {
            @Override
            public void run()
            {
                for(World world : getServer().getWorlds())
                {
                    for(Mob mob : world.getEntitiesByClass(Mob.class))
                    {
                        if(breedingItems.containsKey(mob.getType()))
                        {
                            if(mob.getTarget() != null && mob.getTarget().getType() == EntityType.PLAYER)
                            {
                                Player player = (Player) mob.getTarget();
                                if(player.getInventory().getItemInMainHand().getType() == breedingItems.get(mob.getType()) ||
                                        player.getInventory().getItemInOffHand().getType() == breedingItems.get(mob.getType()))
                                {
                                    mob.setTarget(null);
                                }
                            }
                        }
                    }
                }
            }
        }, 1L, 1L);
    }
    
    @EventHandler
    public void onEntityTargetLivingEntity(EntityTargetLivingEntityEvent event)
    {
        // Check if this is a hostile mob targeting a player holding the breeding item
        if(breedingItems.containsKey(event.getEntityType()) && event.getTarget() != null && event.getTarget().getType() == EntityType.PLAYER)
        {
            Mob mob = (Mob) event.getEntity();
            Player player = (Player) event.getTarget();
            
            if(player.getInventory().getItemInMainHand().getType() == breedingItems.get(event.getEntityType()) ||
                    player.getInventory().getItemInOffHand().getType() == breedingItems.get(event.getEntityType()))
            {
                event.setCancelled(true);
                
                // If the mob is in love, it should be looking for a partner, not following the player
                if(!mob.getPersistentDataContainer().has(inLoveKey, PersistentDataType.BYTE))
                {
                    mob.getPathfinder().moveTo(event.getTarget());
                }
            }
        }
    }
}
