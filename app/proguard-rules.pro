# Sumi release-build keep rules.
#
# Room, Glance, Compose and the lifecycle libraries ship their own consumer rules,
# so only what Sumi itself reaches by name needs listing here.

# Elements are stored in the database by enum constant name ("FIRE"). R8 may
# rename or unbox enums, which would stop stored names mapping back to elements,
# so the enum is kept exactly as written.
-keep enum com.sumi.app.data.Element { *; }
