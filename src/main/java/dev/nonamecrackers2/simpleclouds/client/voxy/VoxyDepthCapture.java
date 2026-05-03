// dev.nonamecrackers2.simpleclouds.client.voxy.VoxyDepthCapture
package dev.nonamecrackers2.simpleclouds.client.voxy;

/**
 * Populated by MixinVoxyRenderSystem at the tail of renderOpaque.
 * Consumed by ShaderAwareVoxySupportPipeline to know which FBO/depth
 * Voxy last rendered into so we can merge it into the cloud target.
 */
public final class VoxyDepthCapture {

    private VoxyDepthCapture() {
    }

    /**
     * The GL_DRAW_FRAMEBUFFER_BINDING that was active when Voxy's renderOpaque
     * started.
     */
    public static int capturedFbo = 0;

    /**
     * Whether Iris was active during the last Voxy render.
     * If true, Voxy rendered through IrisVoxyRenderPipeline and depth
     * lives in Iris's gbuffer — NOT in the vanilla main render target yet.
     */
    public static boolean irisWasActive = false;

    /** Frame counter to detect stale captures (Voxy may skip frames). */
    public static long captureFrame = -1L;
}
