JAVAC := javac
JAVA := java

SRC_DIR := src
OUT_DIR := out
MAIN_CLASS := ringkv.NodeMain

SOURCES := $(shell find $(SRC_DIR) -type f -name '*.java')
STAMP := $(OUT_DIR)/.compiled

.PHONY: all build run clean

all: build

build: $(STAMP)

$(STAMP): $(SOURCES) Makefile
	mkdir -p $(OUT_DIR)
	$(JAVAC) --release 25 -encoding UTF-8 -d $(OUT_DIR) $(SOURCES)
	touch $(STAMP)

run: build
	$(JAVA) -cp $(OUT_DIR) $(MAIN_CLASS)

clean:
	rm -rf $(OUT_DIR)
