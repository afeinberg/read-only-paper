library(ggplot2)

browsemap <- read.table("data/browsemap_time_call_distribution", header=T)
browsemap$name <- "browsemap"
pymk <- read.table("data/pymk_time_call_distribution", header=T)
pymk$name <- "pymk"

r <- merge(browsemap, pymk, all=T)

r <- subset(r, Seconds > 0)

p <- ggplot(aes(x=Seconds, y=Calls, line=name), data=r) +
  geom_line() +
  #facet_wrap(~ name) +
  scale_x_log10() + 
  scale_y_log10() +
  opts(legend.position="top", legend.direction="horizontal") 
