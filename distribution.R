library(ggplot2)
r <-read.csv("data/pymk_data_distribution", sep="\t")
t <-read.csv("data/browsemap_data_distribution", sep="\t")
r$Name <- "PYMK"
t$Name <- "CF"
c <- rbind(r,t)
xbreaks <- c(4,6,8,10,12,14,16)
s <- qplot(Size, Values, data = c, shape = Name, fontsize=14, theme_blank) +
  scale_y_log10("# of tuples") +
  scale_x_log2("size (bytes)", breaks=2^xbreaks, labels=2^xbreaks) +
  opts(legend.position="top", legend.direction="horizontal") + 
  scale_shape(name="")
pdf("images/data_distribution.pdf", width=5, height=4)
print(s, newpage=F)
