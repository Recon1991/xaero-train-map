package net.foxy.xaerotrainmap;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.simibubi.create.compat.trainmap.TrainMapManager;
import com.simibubi.create.compat.trainmap.TrainMapSyncClient;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre;
import xaero.map.gui.GuiMap;

import java.util.List;
import java.lang.reflect.Method;

public class TrainMap {

	private static boolean requesting;

	public TrainMap() {
	}

	public static void tick() {
		if (!AllConfigs.client().showTrainMapOverlay.get() || !(Minecraft.getInstance().screen instanceof GuiMap)) {
			if (requesting)
				TrainMapSyncClient.stopRequesting();
			requesting = false;
			return;
		}
		TrainMapManager.tick();
		requesting = true;
		TrainMapSyncClient.requestData();
	}

	public static void mouseClick(Pre event) {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof GuiMap screen))
			return;

		Window window = mc.getWindow();
		double mX = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
		double mY = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();

		if (TrainMapManager.handleToggleWidgetClick(Mth.floor(mX), Mth.floor(mY), 3, 30))
			event.setCanceled(true);
	}

	// GuiGraphics graphics, Fullscreen screen, double x, double z, int mX, int mY, float pt
	public static void onRender(Screen screen, GuiGraphics graphics, int mX,
								int mY, float pt, double mapScale, double x, double z, int mPosX, int mPosZ) {

		if (!AllConfigs.client().showTrainMapOverlay.get()) {
			renderToggleWidgetAndTooltip(graphics, screen, mX, mY);
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		Window window = mc.getWindow();

		// Use Minecraft's GUI scale directly instead of computing it
		double scale = mapScale / window.getGuiScale();

		// Log detailed platform info to help with macOS scaling differences
		String os = System.getProperty("os.name").toLowerCase();
		LogUtils.getLogger().info("[TrainMap] OS Detected: {}", os);
		LogUtils.getLogger().info("[TrainMap] Screen (raw): {}x{}, GUI scaled: {}x{}, GUI scale: {}, final scale: {}",
				window.getScreenWidth(),
				window.getScreenHeight(),
				window.getGuiScaledWidth(),
				window.getGuiScaledHeight(),
				window.getGuiScale(),
				scale
		);

		PoseStack pose = graphics.pose();
		pose.pushPose();

		pose.translate(screen.width / 2.0f, screen.height / 2.0f, 0);
		pose.scale((float) scale, (float) scale, 1);
		pose.translate(-x, -z, 0);

		Rect2i bounds = new Rect2i(
				Mth.floor(-screen.width / 2.0f / scale + x),
				Mth.floor(-screen.height / 2.0f / scale + z),
				Mth.floor(screen.width / scale),
				Mth.floor(screen.height / scale)
		);

		try {
			// Try to call new signature: (GuiGraphics, int, int, float, boolean, Rect2i)
			Method m = TrainMapManager.class.getDeclaredMethod(
					"renderAndPick",
					GuiGraphics.class,
					int.class,
					int.class,
					float.class,
					boolean.class,
					Rect2i.class
			);
			m.invoke(null, graphics, mPosX, mPosZ, pt, false, bounds);
		} catch (NoSuchMethodException e) {
			try {
				// Fallback to old signature: (GuiGraphics, int, int, boolean, Rect2i)
				Method fallback = TrainMapManager.class.getDeclaredMethod(
						"renderAndPick",
						GuiGraphics.class,
						int.class,
						int.class,
						boolean.class,
						Rect2i.class
				);
				fallback.invoke(null, graphics, mPosX, mPosZ, false, bounds);
			} catch (Throwable t2) {
				LogUtils.getLogger().error("Both signatures of renderAndPick failed.", t2);
			}
		} catch (Throwable t) {
			LogUtils.getLogger().error("Failed to invoke renderAndPick reflectively.", t);
		}

		pose.popPose();

		renderToggleWidgetAndTooltip(graphics, screen, mX, mY);
	}


	private static boolean renderToggleWidgetAndTooltip(GuiGraphics graphics, Screen screen, int mouseX, int mouseY) {
		TrainMapManager.renderToggleWidget(graphics, 3, 30);
		if (!TrainMapManager.isToggleWidgetHovered(mouseX, mouseY, 3, 30))
			return false;

		RemovedGuiUtils.drawHoveringText(graphics, List.of(Component.translatable("create.train_map.toggle")
		), mouseX, mouseY + 20, screen.width, screen.height, 256, Minecraft.getInstance().font);
		return true;
	}

}
