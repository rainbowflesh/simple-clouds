package dev.nonamecrackers2.simpleclouds.common.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

public final class CompatHelper {
	private static final String IRIS_API_CLASSID = "net.irisshaders.iris.api.v0.IrisApi";
	private static Method irisApiGetter;
	private static Method irisShaderPackGetter;

	private CompatHelper() {
	}

	public static boolean areShadersRunning() {
		if (!isIrisLoaded())
			return false;
		Object api = getIrisApi();
		if (api == null)
			return false;
		try {
			return (boolean) irisShaderPackGetter.invoke(api);
		} catch (IllegalAccessException | InvocationTargetException e) {
			return false;
		}
	}

	public static boolean isVrActive() {
		return isVivecraftLoaded();
	}

	public static boolean isVivecraftLoaded() {
		return ModList.get().isLoaded("vivecraft") || isClassPresent("org.vivecraft.client_vr.ClientDataHolderVR");
	}

	public static boolean isIrisLoaded() {
		return ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus") || isClassPresent(IRIS_API_CLASSID);
	}

	private static Object getIrisApi() {
		try {
			if (irisApiGetter == null || irisShaderPackGetter == null) {
				Class<?> irisApiClass = Class.forName(IRIS_API_CLASSID);
				irisApiGetter = irisApiClass.getMethod("getInstance");
				irisShaderPackGetter = irisApiClass.getMethod("isShaderPackInUse");
			}
			return irisApiGetter.invoke(null);
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
				| InvocationTargetException e) {
			return null;
		}
	}

	private static boolean isClassPresent(String className) {
		try {
			Class.forName(className, false, Minecraft.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}