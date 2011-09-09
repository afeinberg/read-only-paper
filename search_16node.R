library(ggplot2)
r <-read.csv("data/search_16node", sep="\t")
uniform <- melt(r, 1, 3)
zipfian <- melt(r, 1, 5)
c <- rbind(uniform, zipfian)
xlab <- c(2,4,6,8,10)
s <- qplot(GB, value, data = c, shape = variable, geom = c("line", "point"), fontsize=14, theme_blank) +
  scale_x_log2("output size (GB)", breaks=2^xlab, labels=2^xlab) +
  scale_y_continuous("median latency (ms)") +
  opts(legend.position="top", legend.direction="horizontal") + 
  scale_shape(name="")

pdf("images/search_16node.pdf", width=5, height=4)
print(s, newpage=F)
