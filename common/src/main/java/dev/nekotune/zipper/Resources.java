package dev.nekotune.zipper;

import dev.nekotune.zipper.definition.DependencyInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class Resources {

    public static interface Gui {
        public static interface Sprites {
            public static interface Widgets {
                public static interface TransferableSelection {
                    public static final Sprite MOVE_UP = new Sprite(
                            ResourceLocation.withDefaultNamespace("transferable_list/move_up"), 32, 32);
                    public static final Sprite MOVE_UP_HIGHLIGHTED = new Sprite(
                            ResourceLocation.withDefaultNamespace("transferable_list/move_up_highlighted"), 32, 32);
                    public static final Sprite MOVE_DOWN = new Sprite(
                            ResourceLocation.withDefaultNamespace("transferable_list/move_down"), 32, 32);
                    public static final Sprite MOVE_DOWN_HIGHLIGHTED = new Sprite(
                            ResourceLocation.withDefaultNamespace("transferable_list/move_down_highlighted"), 32, 32);
                }

                public static interface Dropdown {
                    public static final Sprite OPEN = new Sprite(ResourceLocation.fromNamespaceAndPath(
                            Constants.MOD_ID, "widget/arrow_open"), 20, 20);
                    public static final Sprite CLOSED = new Sprite(ResourceLocation.fromNamespaceAndPath(
                            Constants.MOD_ID, "widget/arrow_closed"), 20, 20);
                    public static final Sprite OPEN_HOVERED = new Sprite(ResourceLocation.fromNamespaceAndPath(
                            Constants.MOD_ID, "widget/arrow_open_hovered"), 20, 20);
                    public static final Sprite CLOSED_HOVERED = new Sprite(ResourceLocation.fromNamespaceAndPath(
                            Constants.MOD_ID, "widget/arrow_closed_hovered"), 20, 20);

                    public static Sprite get(final boolean open, final boolean hovered) {
                        return open ? (hovered ? OPEN_HOVERED : OPEN) : (hovered ? CLOSED_HOVERED : CLOSED);
                    }
                }
            }

            public static interface Icons {
                public static final Sprite EDIT = new Sprite(ResourceLocation.fromNamespaceAndPath(
                        Constants.MOD_ID, "icon/edit"), 20, 20);
                public static final Sprite DELETE = new Sprite(ResourceLocation.fromNamespaceAndPath(
                        Constants.MOD_ID, "icon/delete"), 20, 20);
                public static final Sprite CLOSE = new Sprite(ResourceLocation.fromNamespaceAndPath(
                        Constants.MOD_ID, "icon/close"), 14, 14);
                public static final Sprite SEARCH = new Sprite(ResourceLocation.fromNamespaceAndPath(
                        Constants.MOD_ID, "icon/search"), 12, 12);

                public static interface Host {
                    public static final HostIcons MODRINTH = new HostIcons(
                            new Sprite(ResourceLocation.fromNamespaceAndPath(
                                    Constants.MOD_ID, "icon/modrinth"), 20, 20),
                            new Sprite(ResourceLocation.fromNamespaceAndPath(
                                    Constants.MOD_ID, "icon/modrinth_on"), 20, 20),
                            new Sprite(ResourceLocation.fromNamespaceAndPath(
                                    Constants.MOD_ID, "icon/modrinth_off"), 20, 20));
                    public static final HostIcons CURSEFORGE = new HostIcons(
                            new Sprite(ResourceLocation.fromNamespaceAndPath(
                                    Constants.MOD_ID, "icon/curseforge"), 20, 20),
                            new Sprite(ResourceLocation.fromNamespaceAndPath(
                                    Constants.MOD_ID, "icon/curseforge_on"), 20, 20),
                            new Sprite(ResourceLocation.fromNamespaceAndPath(
                                    Constants.MOD_ID, "icon/curseforge_off"), 20, 20));
                }
            }
        }
    }

    public static interface Lang {
        public static final String KEY = Constants.MOD_ID;

        public static interface Gui {
            public static final String KEY = Lang.KEY + ".gui";

            public static interface Screen {
                public static final String KEY = Gui.KEY + ".screen";
            }

            public static interface Widget {
                public static final String KEY = Gui.KEY + ".widget";

                public static interface Input {
                    public static final String KEY = Widget.KEY + ".input";
                }
            }
        }
    }

    public static record Sprite(ResourceLocation location, int width, int height) {
        public void render(final GuiGraphics guiGraphics, final int x, final int y) {
            guiGraphics.blitSprite(this.location, x, y, this.width, this.height);
        }
    }

    public static record HostIcons(Sprite logo, Sprite buttonOn, Sprite buttonOff) {
        public static HostIcons of(final DependencyInfo.Host host) {
            switch (host) {
                case MODRINTH:
                    return Gui.Sprites.Icons.Host.MODRINTH;
                case CURSEFORGE:
                    return Gui.Sprites.Icons.Host.CURSEFORGE;
            }
            throw new UnsupportedOperationException();
        }
    }

}
