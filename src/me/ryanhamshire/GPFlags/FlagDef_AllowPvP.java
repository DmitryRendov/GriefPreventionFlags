package me.ryanhamshire.GPFlags;

import java.util.Collection;

import me.ryanhamshire.GriefPrevention.EntityEventHandler;
import me.ryanhamshire.GriefPrevention.events.PreventPvPEvent;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

public class FlagDef_AllowPvP extends FlagDefinition
{
    private WorldSettingsManager settingsManager;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreventPvP(PreventPvPEvent event)
    {
        Flag flag = this.GetFlagInstanceAtLocation(event.getClaim().getLesserBoundaryCorner(), null);
        if(flag == null) return;
        
        event.setCancelled(true);
    }
    
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPotionSplash (PotionSplashEvent event)
    {
        //ignore potions not thrown by players
        ThrownPotion potion = event.getPotion();
        ProjectileSource projectileSource = potion.getShooter();
        if(projectileSource == null || !(projectileSource instanceof Player)) return;
        Player thrower = (Player)projectileSource;
        
        //ignore positive potions
        Collection<PotionEffect> effects = potion.getEffects();
        boolean hasNegativeEffect = false;
        for(PotionEffect effect : effects)
        {
            if(!EntityEventHandler.positiveEffects.contains(effect.getType()))
            {
                hasNegativeEffect = true;
                break;
            }
        }
        
        if(!hasNegativeEffect) return;
        
        //if not in a no-pvp world, we don't care
        WorldSettings settings = this.settingsManager.Get(potion.getWorld());
        if(!settings.pvpRequiresClaimFlag) return;
        
        //ignore potions not effecting players or pets
        boolean hasProtectableTarget = false;
        for(LivingEntity effected : event.getAffectedEntities())
        {
            if(effected instanceof Player && effected != thrower)
            {
                hasProtectableTarget = true;
                break;
            }
            else if(effected instanceof Tameable)
            {
                Tameable pet = (Tameable)effected;
                if(pet.isTamed() && pet.getOwner() != null)
                {
                    hasProtectableTarget = true;
                    break;
                }
            }
        }
        
        if(!hasProtectableTarget) return;
        
        //if in a flagged-for-pvp area, allow
        //Inko: except when the damaged entity is in a no-pvp zone
        Flag flag = this.GetFlagInstanceAtLocation(thrower.getLocation(), thrower);
        if(flag != null) {
            flag = this.GetFlagInstanceAtLocation(event.getEntity().getLocation(), null);
            if (flag != null) return;
        }
        //Inko: If a players wants to self harm, let him be so. (fixes Enderpearls not dealing damage)
        if(thrower == event.getEntity()) return;

        //otherwise disallow
        event.setCancelled(true);
        GPFlags.sendMessage(thrower, TextMode.Err, settings.pvpDeniedMessage);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityDamagebyBlock (EntityDamageByBlockEvent event)
    {
        Material tipo = event.getDamager().getType();

        WorldSettings settings = this.settingsManager.Get(event.getEntity().getWorld());
        if(!settings.pvpRequiresClaimFlag) return;

        if(tipo != null) return;

        Entity effected = event.getEntity();
        boolean daCancellare = false;
        if(effected instanceof Player)
        {
            daCancellare = true;
        }
        else if(effected instanceof Tameable)
        {
            Tameable pet = (Tameable) effected;
            if (pet.isTamed() && pet.getOwner() != null) {
                daCancellare = true;
            }
        }
        event.setCancelled(daCancellare);
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onLingeringPotionSplashEvent (LingeringPotionSplashEvent event)
    {
        ThrownPotion potion = event.getEntity();
        ProjectileSource projectileSource = potion.getShooter();
        if(projectileSource == null || !(projectileSource instanceof Player)) return;

        Player thrower = (Player)projectileSource;
        AreaEffectCloud area = event.getAreaEffectCloud();

/*        Collection<PotionEffect> effects = area.getCustomEffects();
        boolean hasNegativeEffect = false;
        if(area.hasCustomEffect(PotionEffectType.POISON)||area.hasCustomEffect(PotionEffectType.HARM))
            hasNegativeEffect = true;
        if(!hasNegativeEffect) return;
*/
        //if not in a no-pvp world, we don't care
        WorldSettings settings = this.settingsManager.Get(potion.getWorld());
        if(!settings.pvpRequiresClaimFlag) return;
/*
        //ignore potions not effecting players or pets
        boolean hasProtectableTarget = false;
        for(LivingEntity effected : event.getAffectedEntities())
        {
            if(effected instanceof Player && effected != thrower)
            {
                hasProtectableTarget = true;
                break;
            }
            else if(effected instanceof Tameable)
            {
                Tameable pet = (Tameable)effected;
                if(pet.isTamed() && pet.getOwner() != null)
                {
                    hasProtectableTarget = true;
                    break;
                }
            }
        }

        if(!hasProtectableTarget) return; */

        //if in a flagged-for-pvp area, allow
        //Inko: except when the damaged entity is in a no-pvp zone
        Flag flag = this.GetFlagInstanceAtLocation(thrower.getLocation(), thrower);
            if (flag != null) return;

        //otherwise disallow
        event.setCancelled(true);
        GPFlags.sendMessage(thrower, TextMode.Err, settings.pvpDeniedMessage);
    }

    //when an entity is set on fire
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityCombustByEntity (EntityCombustByEntityEvent event)
    {
        //handle it just like we would an entity damge by entity event, except don't send player messages to avoid double messages
        //in cases like attacking with a flame sword or flame arrow, which would ALSO trigger the direct damage event handler
        @SuppressWarnings("deprecation")
        EntityDamageByEntityEvent eventWrapper = new EntityDamageByEntityEvent(event.getCombuster(), event.getEntity(), DamageCause.FIRE_TICK, event.getDuration());
        this.handleEntityDamageEvent(eventWrapper, false);
        event.setCancelled(eventWrapper.isCancelled());
    }

    private void handleEntityDamageEvent(EntityDamageByEntityEvent event, boolean sendErrorMessagesToPlayers)
    {
        if(event.getEntityType() != EntityType.PLAYER)
        {
            Entity entity = event.getEntity();
            if(entity instanceof Tameable)
            {
                Tameable pet = (Tameable)entity;
                if(!pet.isTamed() || pet.getOwner() == null) return;
            }
        }

        Entity damager = event.getDamager();
        if(damager == null)  return;

        //if not in a no-pvp world, we don't care
        WorldSettings settings = this.settingsManager.Get(damager.getWorld());
        if(!settings.pvpRequiresClaimFlag) return;

        Projectile projectile = null;
        if(damager instanceof Projectile)
        {
            projectile = (Projectile)damager;
            if(projectile.getShooter() instanceof Player)
            {
                damager = (Player)projectile.getShooter();
            }
        }

        //Lingering are treated separatedly
        if(damager.getType() != EntityType.PLAYER && damager.getType()!= EntityType.AREA_EFFECT_CLOUD && damager.getType()!= EntityType.PRIMED_TNT) return;

        //if in a flagged-for-pvp area, allow
        //Inko: except when the damaged entity is in a no-pvp zone
        Flag flag = this.GetFlagInstanceAtLocation(damager.getLocation(), null);
        if(flag != null) {
            flag = this.GetFlagInstanceAtLocation(event.getEntity().getLocation(), null);
            if (flag != null) return;
        }

        //Inko: Let players self harm
        if(damager == event.getEntity()) return;

        //otherwise disallow
        //Inko: cancel only if the damaged entity is a player
        if(event.getEntityType() == EntityType.PLAYER) {
            event.setCancelled(true);
            if (projectile != null) projectile.remove();
            if (sendErrorMessagesToPlayers && damager instanceof Player)
                GPFlags.sendMessage((Player) damager, TextMode.Err, settings.pvpDeniedMessage);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity (EntityDamageByEntityEvent event)
    {
        this.handleEntityDamageEvent(event, true);
    }
    
    public FlagDef_AllowPvP(FlagManager manager, GPFlags plugin, WorldSettingsManager settingsManager)
    {
        super(manager, plugin);
        this.settingsManager = settingsManager;
    }
    
    @Override
    String getName()
    {
        return "AllowPvP";
    }

    @Override
    MessageSpecifier GetSetMessage(String parameters)
    {
        return new MessageSpecifier(Messages.AddEnablePvP);
    }

    @Override
    MessageSpecifier GetUnSetMessage()
    {
        return new MessageSpecifier(Messages.RemoveEnabledPvP);
    }
}
