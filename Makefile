PROJECT=voldemort
LATEXMK=latexmk
OPTIONS=-g
DVIPS=dvips
PS2PDF=ps2pdf
GS=gs
NOOPT=1

SRCS=$(wildcard *.tex) $(wildcard *.bib) $(wildcard images/*)

DVI=$(PROJECT).dvi
PS=$(PROJECT).ps
PDF=$(PROJECT).pdf

default: pdf

dvi: $(DVI)
ps: $(PS)
pdf: $(PDF)

all: dvi ps pdf 

$(PDF): $(SRCS) pre 
	$(LATEXMK) $(OPTIONS) -pdf $(PROJECT)
ifneq ($(NOOPT),1)
	$(GS) -dBATCH -dNOPAUSE -sDEVICE=pdfwrite -dPDFSETTINGS=/prepress \
	-dEmbedAllFonts=true -dSubsetFonts=true -dMaxSubsetPct=100 \
	-sOutputFile=$(PROJECT).pdf.new -f $(PROJECT).pdf 
	pdfopt $(PROJECT).pdf.new $(PROJECT).pdf
	rm -rf $(PROJECT).pdf.new
endif

pdffromps: $(DVI) pre 
	dvips -Ppdf -G0 $(PROJECT)
	ps2pdf -dPDFSETTINGS=/prepress -dCompatibilityLevel=1.3 \
	-dMaxSubsetPct=100 -dSubsetFonts=true -dEmbedAllFonts=true \
	-dAutoRotatePages=/PageByPage $(PROJECT).ps
	pdfopt $(PROJECT).pdf $(PROJECT).pdf.new
	mv -f $(PROJECT).pdf.new $(PROJECT).pdf
 
$(DVI): $(SRCS) pre
	$(LATEXMK) $(OPTIONS) -dvi $(PROJECT)
	
$(PS): $(SRCS) pre
	$(LATEXMK) $(OPTIONS) -ps $(PROJECT)

pre:

tidy:
	$(LATEXMK) -c $(PROJECT)	

clean:
	$(LATEXMK) -C $(PROJECT)

.PHONY: clean tidy all default dvi ps pdf wc pre abstract

