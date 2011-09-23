library(ggplot2)
r <- read.csv("data/browsemap_raw", sep=" ")
s <- qplot(Time, ms, data = r, fontsize=14, theme_blank, xlab ="time (mins)", ylab ="CF 5-min window average latency (ms)") + 
  opts(legend.position="top", legend.direction="horizontal") + 
  scale_shape(name="") +
  geom_vline(xintercept=18, lty=2) +
  annotate("text", x=42, y=18, label="swap")

pdf("images/browsemap_search.pdf", width=5, height=4)
print(s, newpage=F)
