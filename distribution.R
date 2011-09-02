library(ggplot2)
r <-read.csv("data/pymk_data_distribution", sep="\t")
t <-read.csv("data/browsemap_data_distribution", sep="\t")
r$Name <- "Pymk"
t$Name <- "Browsemaps"
c <- rbind(r,t)
s <- qplot(log(Size), log(Values), data = c, shape = Name, fontsize=14, theme_blank, xlab ="Size in bytes", ylab ="Number of tuples") + opts(legend.position="top", legend.direction="horizontal") + scale_shape(name="")
pdf("images/data_distribution.pdf", width=5, height=4)
print(s, newpage=F)
