
## 1. Pack selection screen

Change DependenciesScreen to a one-column pack list.
The list should be almost identical to a TransferablePackList: display packs with their icon, title, & description; packs can be re-ordered via. up and down arrow buttons which show on hover.

If their local files do not yet exist, list a placeholder pack with no description and a generic icon, either a folder or a web globe depending on if they're downloaded or not.
For the folder icon: the image will be tinted red with an exclamation mark, and hovering will show a tooltip saying "File not found: <file route>".
For the web globe icon: the image will be tinted yellow with an exclamation mark, and hovering will show a tooltip saying "File pending download (requires game restart)".

Packs should show their edit and delete buttons on the right when hovered.


## 2. List packs separately by mode

Packs listed on DependenciesScreens will be separated into "categories" depending on their mode, with mode categories sorted by their loading order.

At the top will be optional packs, both enabled and disabled. Enabled packs are always above disabled packs; disabled packs cannot be re-ordered, as they have no loading priority.

Below that will be forced packs, and further below will be support packs.

Packs may only be re-ordered within their given mode section. 


## 3. Loading priorities handled via. arrows

Remove the loading priority option from DependencyEditScreen.

Loading priority will instead be handled by packs' ordering in DependenciesScreens, controlled by the same arrow buttons that exist in PackSelectionScreens.

Packs can no longer have matching loading priorities.


## 4. Converting regular packs

Packs that are in the default vanilla pack folders (resourcepacks and datapacks) will be listed on DependenciesScreens at the very bottom in their own section.

Instead of edit & delete buttons, regular packs have one "convert" button which changes them into a Zipper pack with default values and the mode OPTIONAL_DISABLED. Clicking this first converts the pack (moves the local file into .zipper subdirectory), and then opens a DependencyEditScreen for the pack.


## 5. Modpack resources

Add a custom "modpack" pack in both the resourcepacks and the datapacks folders. This will be a resource/data pack that always loads above *everything* else and is always shown in the pack list above forced dependencies. This feature is already done, we're just moving where the pack is.

The "modpack" pack is named "Modpack Resources" in-game, and is listed with a cobbled deepslate icon by default to complement the cobblestone icon of the "Mod Resources" packs. This icon is overridden if an icon.png file exists in the root directory (.minecraft). The pack's name is lang-driven, so you can change it to suit your modpack.


## 6. "Add new pack" improvements

Clicking the "add" button on a DependenciesScreen, rather than adding a new empty pack, will open a DependencyEditScreen for a new empty pack, which is added automatically.

The add button is changed to be 20x20 and moved to the bottom-right of the screen into the bottom bar.


## 7. Action safety

Add a confirmation pop-up for clicking the "delete" button for dependencies on a DependenciesScreen.