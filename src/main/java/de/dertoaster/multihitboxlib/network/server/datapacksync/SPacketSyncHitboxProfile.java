package de.dertoaster.multihitboxlib.network.server.datapacksync;

import java.util.Map;

import com.mojang.serialization.Codec;

import commoble.databuddy.data.CodecJsonDataManager;
import de.dertoaster.multihitboxlib.entity.hitbox.HitboxProfile;
import de.dertoaster.multihitboxlib.init.MHLibDatapackLoaders;
import net.minecraft.resources.ResourceLocation;

public class SPacketSyncHitboxProfile extends AbstractSPacketSyncDatapackContent<HitboxProfile, SPacketSyncHitboxProfile> {

	private static final Codec<Map<ResourceLocation, HitboxProfile>> _MAPPER =
            Codec.unboundedMap(ResourceLocation.CODEC, HitboxProfile.CODEC);
	
	@Override
	protected Codec<Map<ResourceLocation, HitboxProfile>> createMapper() {
		return _MAPPER;
	}

	@Override
	protected Codec<HitboxProfile> getCodec() {
		return HitboxProfile.CODEC;
	}
	
	public SPacketSyncHitboxProfile() {
		super();
	}
	
	public SPacketSyncHitboxProfile(Map<ResourceLocation, HitboxProfile> data) {
		super(data);
	}

	@Override
	public CodecJsonDataManager<HitboxProfile> getDatapackmanager() {
		return MHLibDatapackLoaders.HITBOX_PROFILES;
	}

	@Override
	public Class<SPacketSyncHitboxProfile> getPacketClass() {
		return SPacketSyncHitboxProfile.class;
	}

	@Override
	protected SPacketSyncHitboxProfile createFromPacket(Map<ResourceLocation, HitboxProfile> data) {
		return new SPacketSyncHitboxProfile(data);
	}

}
