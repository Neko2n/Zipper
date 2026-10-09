Re-orient to be based around local files, with web downloading as an optional side feature.

New "source" config option for each pack: LOCAL or WEB
The slug, in local context, will be the file path (must include file extension)

The config GUI will change based on the pack's type.
Editing a web dependency will refer to the slug option as a slug, and will give a space to input mirror slugs. It will also show the toggles for modrinth and curseforge.
Editing a local dependency will refer to the slug option as a file path, and won't show the mirror or host settings. The "support" option will also be removed from the list of loading types you can select.

Local dependencies, in the dependency list, will be displayed above web dependencies.
Local and web dependencies are sectioned off from each other by headers and spacers.

When you click "add new dependency", it will show an intermediary prompt asking if it should be a web dependency or a local dependency. These are two square icon buttons that grow slightly when hovered over.
