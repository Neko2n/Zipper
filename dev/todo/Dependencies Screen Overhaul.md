Overhaul the dependencies screen to be a two-column screen identical in appearance to PackSelectionScreen.

The left will be "Disabled", and the right will be "Enabled".

At the bottom of the "Disabled" section are "local" packs that aren't converted to zipper packs yet. These packs are rendered at 50% opacity, with a button for converting them.

All required packs are forced to be below optional packs, and cannot be swapped with them. All "local" packs are forced to be below everything else, and cannot be moved at all until they're converted into zipper packs.

## Locking

All pack entries on the screen show a lock button on hover. This changes between a sealed lock and an unlocked lock icon depending on its state.

The locked button shows on all packs that can be moved or enabled/disabled by default, zipper or otherwise. It disables packs from being changed by users in *any* way.

Locked packs will show a lock icon where their lock button would be when not hovered.

## Hiding

All required packs (including locked enabled packs) that AREN'T zipper packs will have an eyeball button that's always visible. This eyeball button can be toggled to hide the pack from selection screens.

This isn't available for zipper packs because they're already hidden, contained within a credits menu accessible from the modpack resources pack.

Any locked packs in the "disabled" section are forcibly hidden from users.

Hidden packs are highlighted dark red, render with reduced opacity, and are rendered in greyscale.

## Downloading

All zipper packs will show a web icon button on hover which opens a download configuration screen. This is basically just DependencyEditScreen but without the MODE setting and with the download hosts pulled out of their dropdown.

Any zipper packs with active download hosts will show as a "web dependency", with a tiny 12x12 host website icon (or icons if using multiple hosts) always visible to the left of their 32x32 main pack icon.

## Deleting

All zipper packs will have a trash can button that you can click to delete the pack. This functions identically to the existing one.