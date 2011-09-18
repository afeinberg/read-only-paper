library(ggplot2)
r <-read.csv("data/search_1node", sep="\t")
interpolation <- melt(r, 1,2)
binary <- melt(r, 1, 3)
mysql <- melt(r, 1, 4)
xlab <- c(0, 30, 60, 90, 120, 150, 180)
ylab <- c(0.2, 0.4, 0.8, 1.6, 3.2, 6.4, 12.8, 25.6)
c <- rbind(interpolation,binary, mysql)
s <- qplot(Minutes, value, data = c, geom="line", linetype=variable, shape = variable, fontsize=14, theme_blank ) +
         opts(legend.position="top", legend.direction="horizontal", size=0.5) +
         scale_x_continuous("time since swap (mins)", breaks=xlab, labels=xlab) +
         scale_y_log2("median latency (ms)", breaks=ylab, label=ylab) + scale_shape(legend=FALSE)+ scale_linetype_discrete(name="")
# Old way of plotting
# s <- qplot(Minutes, value, data = c, shape = variable, fontsize=14, theme_blank ) + 
#	opts(legend.position="top", legend.direction="horizontal", size=0.5) + 
#	scale_shape(name="") + 
#	scale_x_continuous("time since swap (mins)", breaks=xlab, labels=xlab) +
#	scale_y_log2("median latency (ms)", breaks=ylab, label=ylab)

pdf("images/search_1node.pdf", width=5, height=4)
print(s, newpage=F)
