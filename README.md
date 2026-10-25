## Zipper - Resource & Data Manager for Modpacks

Utility for integrating resource & data packs into modpacks, both from local files and from the web.

=================================================

### Pack Integration

Zipper allows you to more closely integrate resource & data packs into your modpacks.

Add packs in the configuration GUI, where you can decide how packs load and in what order.

### Modpack Resources

Zipper creates a "modpack" folder in your resourcepacks and datapacks folders. Any assets placed in these folders will override any other assets bundled with your modpack.

### Configurable UX

Zipper provides the following settings for configuring the user experience of your modpack:
- **production** - Hides all GUI elements added by Zipper, making the mod invisible to the user.
- **hideForced** - Hides all force-enabled resource & data packs from GUIs.
- **hideBuiltIn** - Hides all built-in resource & data packs from GUIs, including the "mod resources" and "vanilla resources" packs.
- **warnEnabled** - If a pack is not found and cannot be downloaded, should the user see a warning prompting them to report it to the modpack author.
- **promptEnabled** - When new resource packs are downloaded by Zipper, should the user see a prompt telling them to reload, or should the game just seamlessly reload.
- **disableCompatibilityWarnings** - Toggles the red highlight over resource & data packs provided by Zipper that are marked as incompatible with the running Minecraft version.

### Download Packs From the Web

Zipper allows you to configure your modpack to download resource & data packs from the internet, should they not already exist on the user's machine.

This is done through "project slugs", found in the URL for the pack on corresponding distribution websites (currently only supports Modrinth and CurseForge)

You can also configure packs to be downloaded in **support mode**, which will install a pack if it's not already on your machine but *won't load it*, just to support the author.

#### Note About Alternatives

Zipper seeks to provide functionality already made possible by mods like **Paxi** and **Resource Pack Overrides**.

I've had personal gripes with how difficult it is to manage resource & data packs with these tools, so I made Zipper as a means of handling it all in one place.

#### Notes

No generative AI was directly used in the creation of this project. All made by me :)
