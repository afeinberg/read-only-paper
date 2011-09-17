library(ggplot2)
r <-read.csv("data/mysql_vs_read", sep="\t")
r <- melt(r, id=1:2)

r <- subset(r, variable %in% c("Median", "X99th"))
r$variable <- factor(r$variable)
levels(r$variable) <- c("median", "99th quantile")

s <- qplot(QPS, value, data = r, geom=c("point", "line"), shape = Type, fontsize=14, theme_blank, xlab ="throughput (qps)", ylab="latency (ms)") + opts(legend.position="top", legend.direction="horizontal") + scale_shape(name="") + facet_wrap(~variable, scale="free_y")
pdf("images/mysql_vs_read.pdf", width=5, height=4)
print(s, newpage=F)
