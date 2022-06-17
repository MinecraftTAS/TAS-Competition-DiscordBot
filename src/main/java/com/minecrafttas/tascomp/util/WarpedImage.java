package com.minecrafttas.tascomp.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

public class WarpedImage {

	private static Random random = new Random();
	
	public static byte[] makeCaptcha(String code) {
		BufferedImage img = new BufferedImage(250, 80, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		
		g.setColor(Color.white);
		g.fillRect(0, 0, 250, 80);
		
		for (int i = 0; i < 100; i++) {
			g.setColor(rrgb(180, 230));
			int x = random.nextInt(500)-250;
			int y = random.nextInt(300)-80;
			int w = random.nextInt(4)*40;
			int d = random.nextInt(4)*40;
			g.fillOval(x, y, w, d);
		}
		
		g.setFont(new Font("", Font.BOLD, 250 / 5));
		for (int i = 0; i < 5; i++) {
			g.setColor(rrgb(60, 80));
			
			AffineTransform af = new AffineTransform();
			af.setToRotation(Math.PI / 4 * random.nextDouble() * (random.nextBoolean() ? 1 : -1), (250 / 5) * i + (250/5) / 2, 80/2);
			g.setTransform(af);
			g.drawChars(code.toCharArray(), i, 1, (250 / 5) * i, 80 / 2 + (250/5) / 2);
		}
		
		
		for (int i = 0; i < 15; i++) {
			g.setColor(rrgb(180, 230));
			g.setXORMode(rrgb(40, 60));
			int x = random.nextInt(500)-250;
			int y = random.nextInt(300)-80;
			int w = random.nextInt(4)*40;
			int d = random.nextInt(4)*40;
			g.fillOval(x, y, w, d);
		}
		
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		try {
			ImageIO.write(img, "PNG", o);
			o.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return o.toByteArray();
	}
	
	private static Color rrgb(int from, int to) {
		int r = 0;
		int g = 0;
		int b = 0;
		if (from >= 255 && from == to) {
			r = random.nextInt(255);
			g = random.nextInt(255);
			b = random.nextInt(255);
		} else {
			r = from + random.nextInt(to - from);
			g = from + random.nextInt(to - from);
			b = from + random.nextInt(to - from);
		}
		return new Color(r, g, b);
	}
}
