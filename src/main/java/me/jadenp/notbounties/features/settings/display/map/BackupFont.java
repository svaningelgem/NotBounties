package me.jadenp.notbounties.features.settings.display.map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.ChatColor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

public class BackupFont {
    private final Map<Character, boolean[][]> characterMap;
    private static final char UNKNOWN_CHARACTER = '?';
    private static final int LETTER_SPACING = 1;
    public BackupFont(File file) throws IOException {
        Type typeObject = new TypeToken<Map<Character, boolean[][]>>() {}.getType();
        Gson gson = new Gson();

        FileReader nameReader = new FileReader(file);
        characterMap = gson.fromJson(nameReader, typeObject);
        nameReader.close();
    }

    public int getWidth(String text) {
        text = ChatColor.stripColor(text);
        int width = 0;
        for (char c : text.toCharArray()) {
            if (characterMap.containsKey(c)) {
                width+= characterMap.get(c).length;
            } else {
                width+= characterMap.get(UNKNOWN_CHARACTER).length;
            }
            width+= LETTER_SPACING;
        }
        return width;
    }

    public int getHeight() {
        return characterMap.values().iterator().next()[0].length;
    }

    public void drawText(BufferedImage image, int x, int y, String text) {
        Color color = Color.BLACK;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ChatColor.COLOR_CHAR) {
                char code = text.charAt(i + 1);
                if (BountyPosterProvider.colorTranslations.containsKey(code)) {
                    color = BountyPosterProvider.colorTranslations.get(code);
                }
                i++;
            } else {
                displayLetter(image, x, y, c, color);
                x += getWidth(c + "");
            }
            i++;
        }
    }

    private void displayLetter(BufferedImage image, int x, int y, char letter, Color color) {
        boolean[][] displayPattern;
        if (characterMap.containsKey(letter))
            displayPattern = characterMap.get(letter);
        else if (letter == ' ')
            displayPattern = new boolean[characterMap.get(UNKNOWN_CHARACTER).length][getHeight()];
        else
            displayPattern = characterMap.get(UNKNOWN_CHARACTER);
        for (int dx = 0; dx < displayPattern.length; dx++) {
            for (int dy = 0; dy < displayPattern[0].length; dy++) {
                if (displayPattern[dx][dy])
                    try {
                        image.setRGB(x + dx, y - dy, color.getRGB());
                    } catch (ArrayIndexOutOfBoundsException ignored) {
                        // out of bounds image
                    }
            }
        }
    }
}
