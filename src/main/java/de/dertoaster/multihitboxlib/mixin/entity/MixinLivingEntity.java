package de.dertoaster.multihitboxlib.mixin.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.dertoaster.multihitboxlib.api.IModifiableMultipartEntity;
import de.dertoaster.multihitboxlib.api.IMultipartEntity;
import de.dertoaster.multihitboxlib.entity.MHLibPartEntity;
import de.dertoaster.multihitboxlib.entity.hitbox.HitboxProfile;
import de.dertoaster.multihitboxlib.init.MHLibDatapackLoaders;
import de.dertoaster.multihitboxlib.util.BoneInformation;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity implements IMultipartEntity<LivingEntity> {

	private static final EntityDataAccessor<Optional<UUID>> CURRENT_MASTER = SynchedEntityData.defineId(MixinLivingEntity.class, EntityDataSerializers.OPTIONAL_UUID);

	private Optional<HitboxProfile> HITBOX_PROFILE;

	protected Map<String, MHLibPartEntity<LivingEntity>> partMap = new HashMap<>();
	private PartEntity<?>[] partArray;
	
	private boolean hurtFromPart = false;

	public MixinLivingEntity(EntityType<?> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}
	
	@Inject(
			method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V",
			at = @At("TAIL")
			)
	private void mixinConstructor(CallbackInfo ci) {
		// Load base profile
		this.HITBOX_PROFILE = MHLibDatapackLoaders.getHitboxProfile(this.getType());
		
		if(!this.HITBOX_PROFILE.isPresent()) {
			return;
		}
		
		// Initialize map and array
		int partCount = this.HITBOX_PROFILE.isPresent() ? this.HITBOX_PROFILE.get().partConfigs().size() : 0;
		this.partMap = new Object2ObjectArrayMap<>(partCount);
		this.partArray = new PartEntity<?>[partCount];
		
		if(this.HITBOX_PROFILE.isPresent()) {
			// At last, create the parts themselves
			final BiConsumer<String, MHLibPartEntity<LivingEntity>> storageFunction = (str, part) -> {
				int id = 0;
				while(partArray[id] != null) {
					id++;
				}
				this.partArray[id] = part;
				this.partMap.put(str, part);
			};
			this.createSubPartsFromProfile(this.HITBOX_PROFILE.get(), (LivingEntity)((Object)this), storageFunction);
		}

		if (this.isMultipartEntity() && this.getParts() != null) {
			this.setId(Entity.ENTITY_COUNTER.getAndAdd(this.getParts().length + 1) + 1);
		}
	}
	
	@Inject(
			method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void mixinHurt(DamageSource pSource, float pAmount, CallbackInfoReturnable<Boolean> cir) {
		if (pSource.is(DamageTypes.OUT_OF_WORLD)) {
			return;
		}
		
		if (pSource.isCreativePlayer()) {
			//return;
		}
		
		if (this.HITBOX_PROFILE != null && this.HITBOX_PROFILE.isPresent() && !this.HITBOX_PROFILE.get().mainHitboxConfig().canReceiveDamage()) {
			if (!this.hurtFromPart) {
				cir.setReturnValue(false);
				cir.cancel();
			}
		}
	}
	
	@Override
	public boolean hurt(PartEntity<LivingEntity> subPart, DamageSource source, float damage) {
		this.hurtFromPart = true;
		boolean result = IMultipartEntity.super.hurt(subPart, source, damage);
		this.hurtFromPart = false;
		
		return result;
	}

	// After ticking all parts => call alignment code
	// Bind to interface to potentially cancel auto ticking and alignment
	@Inject(
			method = "aiStep",
			at = @At("TAIL")
	)
	private void mixinAiStep(CallbackInfo ci) {
		if(!this.isMultipartEntity()) {
			return;
		}
		
		if (this.HITBOX_PROFILE.isPresent() && this.HITBOX_PROFILE.get().syncToModel()) {
			// EValuate model data
		} else {
			this.alignSubParts((LivingEntity)(Object)this, this.partMap.values());
		}
	}
	
	@Inject(
			method = "defineSynchedData",
			at = @At("TAIL")
	)
	private void mixinDefineSynchedData(CallbackInfo ci) {
		this.entityData.define(CURRENT_MASTER, Optional.empty());
	}
	
	// In tick method => intercept and tick the subparts
	@Inject(
			method = "tick",
			at = @At("TAIL")
	)
	private void mixinTick(CallbackInfo ci) {
		if(!this.isMultipartEntity()) {
			return;
		}
		
		this.tickParts((LivingEntity)(Object)this, this.partMap.values());
	}
	

	@Override
	@Nullable
	public UUID getMasterUUID() {
		// Allow change of logic
		if (this instanceof IModifiableMultipartEntity<?> imme) {
			return imme.getMasterUUID();
		}

		Optional<UUID> stored = this.entityData.get(CURRENT_MASTER);
		if (stored.isPresent()) {
			return stored.get();
		}
		return null;
	}

	@Override
	public void processBoneInformation(Map<String, BoneInformation> boneInformation) {
		// Allow change of logic
		if (this instanceof IModifiableMultipartEntity<?> imme) {
			imme.processBoneInformation(boneInformation);
			return;
		}
		// Process the bones...
		for (Map.Entry<String, BoneInformation> entry : boneInformation.entrySet()) {
			Optional<MHLibPartEntity<LivingEntity>> optPart = this.getPartByName(entry.getKey());
			optPart.ifPresent(part -> {
				
			});
		}
	}
	
	@Override
	public Optional<MHLibPartEntity<LivingEntity>> getPartByName(String name) {
		if (this.partMap == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(this.partMap.getOrDefault(name, null));
	}

	@Override
	public boolean syncWithModel() {
		// Allow change of logic
		if (this instanceof IModifiableMultipartEntity<?> imme) {
			return imme.syncWithModel();
		}
		return this.HITBOX_PROFILE.isPresent() && this.HITBOX_PROFILE.get().syncToModel();
	}

	// Before the constructor gets called => intercept the entityType and modify it's "size" argument to use the mainHitboxSize

	// Intercept defineSynchedData and add our master data

	// Intercept object creation, then create the hitbox profile

	// Add special hurt method => in interface => used for when subparts where damaged

	// Call this after the object has been created => method in interface, needs to be cancellable

	// Override setID to also set the id of the parts
	@Override
	public void setId(int pId) {
		super.setId(pId);
		
		if (this instanceof IModifiableMultipartEntity<?>) {
			return;
		}
		if (this.isMultipartEntity() && this.getParts() != null) {
			for (int i = 0; i < this.getParts().length; i++) {
				this.getParts()[i].setId(pId + i + 1);
			}
		}
	}

	@Override
	public boolean isMultipartEntity() {
		return super.isMultipartEntity() || !this.partMap.values().isEmpty();
	}
	
	@Override
	@Nullable
	public PartEntity<?>[] getParts() {
		if (this instanceof IModifiableMultipartEntity<?>) {
			return super.getParts();
		}
		return this.partArray;
	}

	// Also make sure to modify the result of isMultipartEntity to be correct
	
	@Override
	public float mhlibGetEntityRotationYForPartOffset() {
		if (this instanceof IModifiableMultipartEntity<?> imme) {
			return imme.mhlibGetEntityRotationYForPartOffset();
		}
		
		return this.getYRot();
	}

	public Optional<HitboxProfile> getHitboxProfile() {
		if (this.HITBOX_PROFILE == null) {
			return MHLibDatapackLoaders.getHitboxProfile(this.getType());
		}
		return this.HITBOX_PROFILE;
	}
	
	@Override
	public boolean isPickable() {
		boolean result = super.isPickable();
		
		if(this.HITBOX_PROFILE != null && this.HITBOX_PROFILE.isPresent()) {
			result = result && this.HITBOX_PROFILE.get().mainHitboxConfig().canReceiveDamage();
		}

		return result;
	}

}
