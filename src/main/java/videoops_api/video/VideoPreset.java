
package videoops_api.video;

public enum VideoPreset {

    VERTICAL(720, 1280),
    SQUARE(720, 720),
    LANDSCAPE(1280, 720);

    private final int width;
    private final int height;

    VideoPreset(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
