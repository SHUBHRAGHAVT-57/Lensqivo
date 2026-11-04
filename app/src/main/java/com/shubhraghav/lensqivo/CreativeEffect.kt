package com.shubhraghav.lensqivo

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.shubhraghav.lensqivo.core.Look
import kotlin.math.min

data class RenderRecipe(
    val look: Look = Look.ORIGINAL,
    val strength: Float = .7f,
    val smooth: Float = 0f,
    val glow: Float = 0f,
) {
    fun isOriginal(): Boolean = (look == Look.ORIGINAL || strength.amount() == 0f) &&
        smooth.amount() == 0f && glow.amount() == 0f
}

private fun Float.amount(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f

@androidx.annotation.OptIn(UnstableApi::class)
class CreativeEffect(private val parameters: () -> RenderRecipe) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("Creative looks require SDR BT.709 input")
        return try {
            CreativeShaderProgram(context, parameters)
        } catch (error: Exception) {
            throw VideoFrameProcessingException.from(error)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class CreativeShaderProgram(context: Context, private val parameters: () -> RenderRecipe) :
    BaseGlShaderProgram(false, 1) {
    private val shader = CreativeShader(context, false)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        shader.configure(inputWidth, inputHeight, sampleRadius(inputWidth, inputHeight))
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            // Capture one immutable snapshot; sliders cannot change half of a frame's parameters.
            shader.draw(inputTexId, parameters())
        } catch (error: Exception) {
            throw VideoFrameProcessingException.from(error, presentationTimeUs)
        }
    }

    override fun release() {
        try { shader.close() }
        catch (error: Exception) { throw VideoFrameProcessingException.from(error) }
        finally { super.release() }
    }
}

internal fun sampleRadius(width: Int, height: Int): Float =
    (min(width, height) / 540f).coerceIn(1f, 8f)

/** Same linear BT.709 transform for Media3 textures and explicitly sRGB-decoded JPEG tiles. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class CreativeShader(context: Context, jpeg: Boolean) : AutoCloseable {
    private val program = GlProgram(context, "shaders/creative.vert", "shaders/creative.frag")
    private val texel = FloatArray(2)
    private var previous: RenderRecipe? = null

    init {
        try {
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4)
            program.setIntUniform("uJpeg", if (jpeg) 1 else 0)
        } catch (error: Exception) {
            program.delete()
            throw error
        }
    }

    fun configure(width: Int, height: Int, radius: Float) {
        texel[0] = radius / width
        texel[1] = radius / height
        program.setFloatsUniform("uTexelStep", texel)
    }

    fun draw(texture: Int, recipe: RenderRecipe) {
        program.use()
        if (previous != recipe) {
            program.setFloatsUniform("uColorMatrix", recipe.look.matrix(recipe.strength.amount()))
            program.setFloatUniform("uSmooth", recipe.smooth.amount())
            program.setFloatUniform("uGlow", recipe.glow.amount())
            previous = recipe
        }
        program.setSamplerTexIdUniform("uTexSampler", texture, 0)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    override fun close() { program.delete() }
}
