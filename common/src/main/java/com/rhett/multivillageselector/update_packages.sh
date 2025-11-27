#!/bin/bash

# Update package declarations
sed -i '' 's/^package com\.rhett\.multivillageselector;$/package com.rhett.multivillageselector.util;/' util/BiomeTagExpander.java
sed -i '' 's/^package com\.rhett\.multivillageselector;$/package com.rhett.multivillageselector.config;/' config/ConfigState.java config/ConfigLoader.java config/ConfigParser.java config/MVSConfig.java
sed -i '' 's/^package com\.rhett\.multivillageselector;$/package com.rhett.multivillageselector.strategy;/' strategy/MVSStrategyHandler.java strategy/VanillaStrategyHandler.java strategy/StructurePicker.java strategy/StructureInterceptor.java
sed -i '' 's/^package com\.rhett\.multivillageselector;$/package com.rhett.multivillageselector.commands;/' commands/MVSCommands.java

# Add import for PatternMatcher in BiomeTagExpander
sed -i '' '/^package com.rhett.multivillageselector.util;/a\
\
import com.rhett.multivillageselector.util.PatternMatcher;
' util/BiomeTagExpander.java

