package com.darkender.plugins.passivehostiles;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class PassiveHostiles extends JavaPlugin implements Listener
{
    private NamespacedKey lastBredKey;
    private NamespacedKey lastInLoveKey;
    private Map<EntityType, Material> breedingItems;
    private static final long BREEDING_COOLDOWN = 5 * 60 * 1000;
    private static final long LOVE_COOLDOWN = 30 * 1000;
    private long currentTick = 0;
    private final Random random = new Random();
    
    @Override
    public void onEnable()
    {
        lastBredKey = new NamespacedKey(this, "last-bred");
        lastInLoveKey = new NamespacedKey(this, "last-in-love");
        breedingItems = new HashMap<>();
        breedingItems.put(EntityType.CREEPER, Material.GUNPOWDER);
        breedingItems.put(EntityType.ZOMBIE, Material.ROTTEN_FLESH);
        breedingItems.put(EntityType.SKELETON, Material.BONE);
        breedingItems.put(EntityType.SPIDER, Material.STRING);
        breedingItems.put(EntityType.CAVE_SPIDER, Material.STRING);
        breedingItems.put(EntityType.BLAZE, Material.BLAZE_ROD);
        breedingItems.put(EntityType.DROWNED, Material.ROTTEN_FLESH);
        breedingItems.put(EntityType.HUSK, Material.ROTTEN_FLESH);
        breedingItems.put(EntityType.PHANTOM, Material.PHANTOM_MEMBRANE);
        breedingItems.put(EntityType.STRAY, Material.BONE);
        
        getServer().getPluginManager().registerEvents(this, this);
        
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable()
        {
            @Override
            public void run()
            {
                currentTick++;
                for(World world : getServer().getWorlds())
                {
                    for(Mob mob : world.getEntitiesByClass(Mob.class))
                    {
                        if(!breedingItems.containsKey(mob.getType()))
                        {
                            continue;
                        }
                        
                        // Run this on a timer to interrupt hostile mob targeting when a player
                        // switches to the breeding item *after* the mob started targeting
                        if(mob.getTarget() != null && mob.getTarget().getType() == EntityType.PLAYER)
                        {
                            Player player = (Player) mob.getTarget();
                            if(player.getInventory().getItemInMainHand().getType() == breedingItems.get(mob.getType()) ||
                                    player.getInventory().getItemInOffHand().getType() == breedingItems.get(mob.getType()))
                            {
                                mob.setTarget(null);
                            }
                        }
                        
                        // Every second, pathfind to other mobs of the same type in love
                        if(currentTick % 20 == 0 && isInLove(mob))
                        {
                            Mob closest = null;
                            double closestDistance = Double.MAX_VALUE;
                            for(Entity e : mob.getNearbyEntities(8, 8, 8))
                            {
                                if(e.getType() != mob.getType())
                                {
                                    continue;
                                }
                                Mob check = (Mob) e;
                                if(!isInLove(check))
                                {
                                    continue;
                                }
                                
                                double distance = check.getLocation().distance(mob.getLocation());
                                if(distance < closestDistance)
                                {
                                    closest = check;
                                    closestDistance = distance;
                                }
                            }
                            
                            if(closest != null)
                            {
                                mob.getPathfinder().moveTo(closest);
                                if(closestDistance < 1.5)
                                {
                                    Location l1 = mob.getLocation();
                                    Location l2 = closest.getLocation();
                                    Location spawnLoc = new Location(mob.getWorld(),
                                            (l1.getX() + l2.getX()) / 2.0,
                                            (l1.getY() + l2.getY()) / 2.0,
                                            (l1.getZ() + l2.getZ()) / 2.0);
                                    for(int i = 0; i < 10; i++)
                                    {
                                        spawnLoc.getWorld().spawnParticle(Particle.HEART, spawnLoc.clone().add(
                                                random.nextDouble() - 0.5,
                                                random.nextDouble() + 0.5,
                                                random.nextDouble() - 0.5), 1);
                                    }
                                    spawnLoc.getWorld().spawn(spawnLoc, mob.getClass());
                                    ExperienceOrb experience = spawnLoc.getWorld().spawn(spawnLoc, ExperienceOrb.class);
                                    experience.setExperience(random.nextInt(6) + 1);
                                    
                                    mob.getPersistentDataContainer().set(lastInLoveKey, PersistentDataType.LONG, 0L);
                                    closest.getPersistentDataContainer().set(lastInLoveKey, PersistentDataType.LONG, 0L);
                                    mob.getPersistentDataContainer().set(lastBredKey, PersistentDataType.LONG, System.currentTimeMillis());
                                    closest.getPersistentDataContainer().set(lastBredKey, PersistentDataType.LONG, System.currentTimeMillis());
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
                if(!isInLove(mob))
                {
                    mob.getPathfinder().moveTo(event.getTarget());
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event)
    {
        if(breedingItems.containsKey(event.getRightClicked().getType()))
        {
            Mob mob = (Mob) event.getRightClicked();
            ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
            if(item.getType() == breedingItems.get(mob.getType()) && canBreed(mob) && !isInLove(mob))
            {
                mob.getPersistentDataContainer().set(lastInLoveKey, PersistentDataType.LONG, System.currentTimeMillis());
                
                Location loc = mob.getLocation().add(0.0, mob.getHeight() - 0.5, 0.0);
                for(int i = 0; i < 10; i++)
                {
                    mob.getWorld().spawnParticle(Particle.HEART, loc.clone().add(
                            random.nextDouble() - 0.5,
                            random.nextDouble() - 0.5,
                            random.nextDouble() - 0.5), 1);
                }
                
                if(event.getPlayer().getGameMode() != GameMode.CREATIVE)
                {
                    item.setAmount(item.getAmount() - 1);
                }
            }
        }
    }
    
    public boolean isInLove(Mob mob)
    {
        long lastInLove = mob.getPersistentDataContainer().getOrDefault(lastInLoveKey, PersistentDataType.LONG, 0L);
        return lastInLove > System.currentTimeMillis() - LOVE_COOLDOWN;
    }
    
    public boolean canBreed(Mob mob)
    {
        long lastBred = mob.getPersistentDataContainer().getOrDefault(lastBredKey, PersistentDataType.LONG, 0L);
        return lastBred < System.currentTimeMillis() - BREEDING_COOLDOWN;
    }
}
