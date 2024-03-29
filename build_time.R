library(ggplot2)
r <- read.csv("data/build_time", sep="\t")
p <- melt(r, id=1:2)
s <- qplot(Size, value / 60, data = p, geom =c("point", "line"), shape = variable, fontsize=14, theme_blank, xlab ="size (GB)", ylab ="time (mins)") +
  opts(legend.position="top", legend.direction="horizontal") + 
  scale_shape(name="")

pdf("images/build.pdf", width=5, height=4)
print(s, newpage=F)
