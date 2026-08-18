package com.minecraftai.mod.hud;

public class HudConfig {
    public enum Mode {
        OFF,
        COMPACT,
        NORMAL,
        DETAILED
    }

    public enum Position {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    private boolean enabled = true;
    private Mode mode = Mode.NORMAL;
    private Position position = Position.TOP_LEFT;
    private float scale = 1.0f;
    private float opacity = 0.85f;
    private boolean autoCollapse = false;
    private boolean showUsage = true;
    private boolean showContext = true;
    private boolean showCurrentTool = true;
    private boolean showProject = true;
    private boolean showPermissionMode = true;

    // Notifications
    private boolean completionSound = true;
    private boolean waitingSound = true;
    private boolean showToast = true;
    private boolean showActionBar = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode != null ? mode : Mode.NORMAL;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Position getPosition() {
        return position != null ? position : Position.TOP_LEFT;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public float getScale() {
        return scale > 0.3f ? scale : 1.0f;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public float getOpacity() {
        return Math.max(0.1f, Math.min(1.0f, opacity));
    }

    public void setOpacity(float opacity) {
        this.opacity = opacity;
    }

    public boolean isAutoCollapse() {
        return autoCollapse;
    }

    public void setAutoCollapse(boolean autoCollapse) {
        this.autoCollapse = autoCollapse;
    }

    public boolean isShowUsage() {
        return showUsage;
    }

    public void setShowUsage(boolean showUsage) {
        this.showUsage = showUsage;
    }

    public boolean isShowContext() {
        return showContext;
    }

    public void setShowContext(boolean showContext) {
        this.showContext = showContext;
    }

    public boolean isShowCurrentTool() {
        return showCurrentTool;
    }

    public void setShowCurrentTool(boolean showCurrentTool) {
        this.showCurrentTool = showCurrentTool;
    }

    public boolean isShowProject() {
        return showProject;
    }

    public void setShowProject(boolean showProject) {
        this.showProject = showProject;
    }

    public boolean isShowPermissionMode() {
        return showPermissionMode;
    }

    public void setShowPermissionMode(boolean showPermissionMode) {
        this.showPermissionMode = showPermissionMode;
    }

    public boolean isCompletionSound() {
        return completionSound;
    }

    public void setCompletionSound(boolean completionSound) {
        this.completionSound = completionSound;
    }

    public boolean isWaitingSound() {
        return waitingSound;
    }

    public void setWaitingSound(boolean waitingSound) {
        this.waitingSound = waitingSound;
    }

    public boolean isShowToast() {
        return showToast;
    }

    public void setShowToast(boolean showToast) {
        this.showToast = showToast;
    }

    public boolean isShowActionBar() {
        return showActionBar;
    }

    public void setShowActionBar(boolean showActionBar) {
        this.showActionBar = showActionBar;
    }

    public void cycleMode() {
        switch (getMode()) {
            case OFF: setMode(Mode.COMPACT); break;
            case COMPACT: setMode(Mode.NORMAL); break;
            case NORMAL: setMode(Mode.DETAILED); break;
            case DETAILED: default: setMode(Mode.OFF); break;
        }
    }
}
