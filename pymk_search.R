library(ggplot2)
r <- read.csv("data/pymk_raw", sep=" ")
s <- qplot(Time, ms, data = r, fontsize=14, theme_blank, xlab ="time (mins)", ylab ="PYMK 5-min window average latency (ms)") + 
  opts(legend.position="top", legend.direction="horizontal") + 
  scale_shape(name="") +
  geom_vline(xintercept=11, lty=2) +
  annotate("text", x=35, y=18, label="swap")

pdf("images/pymk_search.pdf", width=5, height=4)
print(s, newpage=F)
