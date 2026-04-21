package dev.nonamecrackers2.simpleclouds.client.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nonamecrackers2.crackerslib.common.compat.CompatHelper;

public class SimpleCloudsIrisCompatHandler {
    private static final Logger LOGGER = LogManager.getLogger("simpleclouds/SimpleCloudsIrisCompatHandler");
    private static final String IRIS_API_CLASSID = "net.irisshaders.iris.api.v0.IrisApi";
    private static @Nullable Method irisApiGetter;
    private static @Nullable Method shaderPackInUseGetter;
    private static @Nullable Method shadowPassGetter;
    private static boolean thrownError;

    private static boolean prepareApiMethods() {
        if (!CompatHelper.isIrisLoaded())
            return false;
        if (irisApiGetter != null && shaderPackInUseGetter != null && shadowPassGetter != null)
            return true;

        try {
            Class<?> irisApiClass = Class.forName(IRIS_API_CLASSID);
            irisApiGetter = irisApiClass.getMethod("getInstance");
            shaderPackInUseGetter = irisApiClass.getMethod("isShaderPackInUse");
            shadowPassGetter = irisApiClass.getMethod("isRenderingShadowPass");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
            if (!thrownError) {
                LOGGER.error("Error preparing Iris API methods", e);
                thrownError = true;
            }
            return false;
        }
    }

    private static @Nullable Object getIrisApi() {
        if (!prepareApiMethods())
            return null;

        try {
            return irisApiGetter.invoke(null);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            if (!thrownError) {
                LOGGER.error("Error getting Iris API instance", e);
                thrownError = true;
            }
            return null;
        }
    }

    public static boolean isShaderPackInUse() {
        Object api = getIrisApi();
        if (api == null)
            return false;

        try {
            return (boolean) shaderPackInUseGetter.invoke(api);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            if (!thrownError) {
                LOGGER.error("Error checking if an Iris shader pack is in use", e);
                thrownError = true;
            }
            return false;
        }
    }

    public static boolean isRenderingShadowPass() {
        Object api = getIrisApi();
        if (api == null)
            return false;

        try {
            return (boolean) shadowPassGetter.invoke(api);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            if (!thrownError) {
                LOGGER.error("Error checking if Iris is rendering a shadow pass", e);
                thrownError = true;
            }
            return false;
        }
    }
}