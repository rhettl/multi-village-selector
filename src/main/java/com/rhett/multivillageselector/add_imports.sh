#!/bin/bash

# util/BiomeTagExpander.java - Import PatternMatcher  
sed -i '' '/^package com.rhett.multivillageselector.util;/a\
\
import com.rhett.multivillageselector.util.PatternMatcher;
' util/BiomeTagExpander.java 2>/dev/null || true

# config files - Import MVSConfig where needed
sed -i '' '/^package com.rhett.multivillageselector.config;/a\
\
import com.rhett.multivillageselector.config.MVSConfig;
' config/ConfigState.java config/ConfigParser.java 2>/dev/null || true

# config/ConfigLoader.java - Import MultiVillageSelector
sed -i '' '/^package com.rhett.multivillageselector.config;/a\
\
import com.rhett.multivillageselector.MultiVillageSelector;
' config/ConfigLoader.java 2>/dev/null || true

# config/MVSConfig.java - Import utilities and MultiVillageSelector
sed -i '' '/^package com.rhett.multivillageselector.config;/a\
\
import com.rhett.multivillageselector.MultiVillageSelector;\
import com.rhett.multivillageselector.util.PatternMatcher;\
import com.rhett.multivillageselector.util.BiomeTagExpander;\
import com.rhett.multivillageselector.strategy.StructurePicker;
' config/MVSConfig.java 2>/dev/null || true

# strategy files - Import MVSConfig
sed -i '' '/^package com.rhett.multivillageselector.strategy;/a\
\
import com.rhett.multivillageselector.config.MVSConfig;
' strategy/StructurePicker.java strategy/MVSStrategyHandler.java strategy/VanillaStrategyHandler.java strategy/StructureInterceptor.java 2>/dev/null || true

# strategy handlers - Import MultiVillageSelector
sed -i '' '/import com.rhett.multivillageselector.config.MVSConfig;/a\
import com.rhett.multivillageselector.MultiVillageSelector;
' strategy/MVSStrategyHandler.java strategy/StructureInterceptor.java 2>/dev/null || true

# commands/MVSCommands.java - Import MVSConfig
sed -i '' '/^package com.rhett.multivillageselector.commands;/a\
\
import com.rhett.multivillageselector.config.MVSConfig;
' commands/MVSCommands.java 2>/dev/null || true

