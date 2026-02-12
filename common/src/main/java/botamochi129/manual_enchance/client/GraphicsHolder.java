package botamochi129.manual_enchance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
#if MC_VERSION >= "12000"
import net.minecraft.client.gui.GuiGraphics;
#else
import net.minecraft.client.gui.GuiComponent;
#endif

public class GraphicsHolder {

    #if MC_VERSION >= "12000"
    public GuiGraphics graphics;
    #endif
    public PoseStack matrixStack;

    public void setupContext(Object matrixStackObj) {
        #if MC_VERSION >= "12000" 
        this.graphics = (GuiGraphics) matrixStackObj;
        this.matrixStack = this.graphics.pose();
        #else
        this.matrixStack = (PoseStack) matrixStackObj;
        #endif
    }

    public void drawFill(int x1, int y1, int x2, int y2, int color) {
        #if MC_VERSION >= "12000" 
        graphics.fill(x1, y1, x2, y2, color);
        #else
        GuiComponent.fill(matrixStack, x1, y1, x2, y2, color);
        #endif
    }

    public void drawText(String text, int x, int y, int color, boolean shadow, int light) {
        Font font = Minecraft.getInstance().font;
        #if MC_VERSION >= "12000" 
        graphics.drawString(font, text, x, y, color, shadow);
        #else
        if (shadow) {
            font.drawShadow(matrixStack, text, x, y, color);
        } else {
            font.draw(matrixStack, text, x, y, color);
        }
        #endif
    }

    public void drawCenteredText(String text, int cx, int y, int color, boolean shadow, int light) {
        int width = Minecraft.getInstance().font.width(text);
        drawText(text, cx - width / 2, y, color, shadow, light);
    }
}